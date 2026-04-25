#include "stdafx.h"
#include "AmanServer.h"
#include <iostream>
#include <chrono>
#include <thread>
#include <atomic>
#include <winsock2.h>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <string>
#include <windows.h>
#include <ws2tcpip.h>

#pragma comment(lib, "ws2_32.lib")  // Link with the Winsock library

namespace {
constexpr uint32_t MAX_FRAME_SIZE = 16 * 1024 * 1024;
constexpr int SOCKET_BUFFER_SIZE = 256 * 1024;
}

// Helper function for debug logging in DLLs
void DebugOut(const std::string& message) {
    std::string logMsg = "[AmanServer] " + message + "\n";
    OutputDebugStringA(logMsg.c_str());
}

AmanServer::AmanServer() : isRunning(false), clientConnected(false), listenSocket(INVALID_SOCKET), clientSocket(INVALID_SOCKET) {
    startServer();
}

AmanServer::~AmanServer() {
    stop();
    WSACleanup();  // Clean up Winsock
}

void AmanServer::startServer() {
    // Initialize Winsock
    WSADATA wsaData;
    int result = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (result != 0) {
        DebugOut("WSAStartup failed: " + std::to_string(result));
        return;
    }

    // Start the server thread
    isRunning = true;
    serverThread = std::thread(&AmanServer::serverLoop, this);
    senderThread = std::thread(&AmanServer::senderThreadLoop, this);
}

void AmanServer::stop() {
    if (isRunning) {
        isRunning = false;
        clientConnected = false;
        
        // Close the listen socket first to unblock accept()
        if (listenSocket != INVALID_SOCKET) {
            closesocket(listenSocket);
            listenSocket = INVALID_SOCKET;
        }
        
        // Close the client socket
        if (clientSocket != INVALID_SOCKET) {
            closesocket(clientSocket);
            clientSocket = INVALID_SOCKET;
        }
        
        // Notify condition variable to unblock any waiting threads
        queueCondition.notify_all();
        
        // Wait for threads to finish
        if (serverThread.joinable()) {
            serverThread.join();
        }
        if (senderThread.joinable()) {
            senderThread.join();
        }
    }
}

void AmanServer::serverLoop() {
    while (isRunning) {
        listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (listenSocket == INVALID_SOCKET) {
            DebugOut("Error creating socket");
            return;
        }

        sockaddr_in serverAddr{};
        serverAddr.sin_family = AF_INET;
        serverAddr.sin_addr.s_addr = INADDR_ANY;
        serverAddr.sin_port = htons(12345);
        
        if (bind(listenSocket, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
            DebugOut("Bind failed with error: " + std::to_string(WSAGetLastError()));
            closesocket(listenSocket);
            listenSocket = INVALID_SOCKET;
            return;
        }

        if (listen(listenSocket, 1) == SOCKET_ERROR) {
            DebugOut("Listen failed with error: " + std::to_string(WSAGetLastError()));
            closesocket(listenSocket);
            listenSocket = INVALID_SOCKET;
            return;        }

        DebugOut("Waiting for a client to connect...");

        clientSocket = accept(listenSocket, nullptr, nullptr);

        // accept() will return SOCKET_ERROR if listenSocket is closed
        if (clientSocket == INVALID_SOCKET) {
            if (isRunning) { // real error
                DebugOut("Accept failed with error: " + std::to_string(WSAGetLastError()));
            }
            if (listenSocket != INVALID_SOCKET) {
                closesocket(listenSocket);
                listenSocket = INVALID_SOCKET;
            }
            continue;
        }        // Client connected - close listen socket and handle communication
        if (listenSocket != INVALID_SOCKET) {
            closesocket(listenSocket);
            listenSocket = INVALID_SOCKET;
        }

        configureClientSocket();

        clientConnected = true;
        DebugOut("Client connected successfully");
          // Notify sender thread that client is connected
        queueCondition.notify_all();
        
        // Notify derived class that client connected (for version handshake)
        onClientConnected();
        
        handleClientConnection();
        
        // Client disconnected
        DebugOut("Cleaning up client connection...");
        clientConnected = false;
        
        // Notify sender thread that client disconnected
        queueCondition.notify_all();
        {
            std::lock_guard<std::mutex> lock(inboundQueueMutex);
            while (!inboundMessageQueue.empty()) {
                inboundMessageQueue.pop();
            }
        }
        if (clientSocket != INVALID_SOCKET) {
            closesocket(clientSocket);
            clientSocket = INVALID_SOCKET;
        }
    }
}

void AmanServer::handleClientConnection() {
    char buffer[4096];
    std::string receivedData;
    
    DebugOut("Starting client communication loop...");
    
    while (isRunning && clientConnected && clientSocket != INVALID_SOCKET) {
        int bytesReceived = recv(clientSocket, buffer, sizeof(buffer), 0);
        if (bytesReceived > 0) {
            receivedData.append(buffer, bytesReceived);
            
            while (receivedData.size() >= sizeof(uint32_t)) {
                uint32_t networkLength = 0;
                std::memcpy(&networkLength, receivedData.data(), sizeof(networkLength));
                uint32_t messageLength = ntohl(networkLength);

                if (messageLength == 0 || messageLength > MAX_FRAME_SIZE) {
                    DebugOut("Invalid frame length from client: " + std::to_string(messageLength));
                    onClientDisconnected();
                    return;
                }

                const size_t frameLength = sizeof(uint32_t) + messageLength;
                if (receivedData.size() < frameLength) {
                    break;
                }

                std::string message = receivedData.substr(sizeof(uint32_t), messageLength);
                receivedData.erase(0, frameLength);

                if (!message.empty()) {
                    enqueueInboundMessage(message);
                }
            }
        } else if (bytesReceived == 0) {
            DebugOut("Client disconnected gracefully (recv returned 0)");
            onClientDisconnected();
            break;
        } else {
            int error = WSAGetLastError();

            if (isRunning && clientConnected) {
                DebugOut("Recv failed with error: " + std::to_string(error) + " (WSAECONNRESET=" + std::to_string(WSAECONNRESET) + ")");
            }
            
            // Check if it's a connection reset or client disconnect
            if (error == WSAECONNRESET || error == WSAECONNABORTED) {
                DebugOut("Client connection was reset/aborted");
                onClientDisconnected();
            }
            break;
        }    }
    
    DebugOut("Client communication loop ended");
}

void AmanServer::configureClientSocket() {
    int tcpNoDelay = 1;
    if (setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char*>(&tcpNoDelay), sizeof(tcpNoDelay)) == SOCKET_ERROR) {
        DebugOut("Failed to set TCP_NODELAY: " + std::to_string(WSAGetLastError()));
    }

    int sendBufferSize = SOCKET_BUFFER_SIZE;
    if (setsockopt(clientSocket, SOL_SOCKET, SO_SNDBUF, reinterpret_cast<const char*>(&sendBufferSize), sizeof(sendBufferSize)) == SOCKET_ERROR) {
        DebugOut("Failed to set send buffer size: " + std::to_string(WSAGetLastError()));
    }

    int receiveBufferSize = SOCKET_BUFFER_SIZE;
    if (setsockopt(clientSocket, SOL_SOCKET, SO_RCVBUF, reinterpret_cast<const char*>(&receiveBufferSize), sizeof(receiveBufferSize)) == SOCKET_ERROR) {
        DebugOut("Failed to set receive buffer size: " + std::to_string(WSAGetLastError()));
    }
}

void AmanServer::enqueueInboundMessage(const std::string& data) {
    std::lock_guard<std::mutex> lock(inboundQueueMutex);
    inboundMessageQueue.push(data);
}

void AmanServer::drainInboundMessages() {
    std::queue<std::string> messagesToProcess;
    {
        std::lock_guard<std::mutex> lock(inboundQueueMutex);
        std::swap(messagesToProcess, inboundMessageQueue);
    }

    while (!messagesToProcess.empty()) {
        auto message = messagesToProcess.front();
        messagesToProcess.pop();

        DebugOut("Processing client message (" + std::to_string(message.length()) + " bytes)");
        try {
            processMessage(message);
        } catch (const std::exception& e) {
            DebugOut("Error processing message: " + std::string(e.what()));
        }
    }
}

bool AmanServer::sendMessageSafely(const std::string& message) {
    if (clientSocket == INVALID_SOCKET || !clientConnected) {
        return false;
    }
    if (message.length() > MAX_FRAME_SIZE) {
        DebugOut("Message too large to send: " + std::to_string(message.length()) + " bytes");
        return false;
    }

    uint32_t networkLength = htonl(static_cast<uint32_t>(message.length()));
    std::string frame;
    frame.resize(sizeof(networkLength) + message.length());
    std::memcpy(&frame[0], &networkLength, sizeof(networkLength));
    std::memcpy(&frame[sizeof(networkLength)], message.data(), message.length());

    const char* data = frame.data();
    int totalBytes = (int)frame.length();
    int bytesSent = 0;
    while (bytesSent < totalBytes && clientConnected && clientSocket != INVALID_SOCKET && isRunning) {
        int result = send(clientSocket, data + bytesSent, totalBytes - bytesSent, 0);
        
        if (result > 0) {
            bytesSent += result;
        } else if (result == SOCKET_ERROR) {
            int error = WSAGetLastError();
            DebugOut("Send failed with error: " + std::to_string(error));
            return false;
        } else {
            DebugOut("Send returned 0, connection closed");
            return false;
        }
    }
    
    if (bytesSent == totalBytes) {
        return true;
    } else {
        DebugOut("Failed to send complete frame: " + std::to_string(bytesSent) + "/" + std::to_string(totalBytes) + " bytes");
        return false;
    }
}

void AmanServer::senderThreadLoop() {
    DebugOut("Sender thread started");
    
    while (isRunning) {
        std::unique_lock<std::mutex> lock(queueMutex);
        DebugOut("Sender thread waiting for client connection or messages...");
        
        // Wait for either a client to connect AND have messages, or for shutdown
        queueCondition.wait(lock, [this] {
            return ((!messageQueue.empty() || !latestMessageKeys.empty()) && clientConnected) || !isRunning;
        });
        
        if (!isRunning) {
            DebugOut("Sender thread stopping - isRunning false");
            break;
        }
        
        if (!clientConnected) {
            DebugOut("Sender thread: No client connected, continuing to wait...");
            continue;
        }
        
        DebugOut("Sender thread woke up, queue size: " + std::to_string(messageQueue.size()) +
            ", latest queue size: " + std::to_string(latestMessageKeys.size()));
        
        while ((!messageQueue.empty() || !latestMessageKeys.empty()) && clientConnected && clientSocket != INVALID_SOCKET && isRunning) {
            std::string message;
            if (!messageQueue.empty()) {
                message = messageQueue.front();
                messageQueue.pop();
            } else {
                auto key = latestMessageKeys.front();
                latestMessageKeys.pop();
                queuedLatestMessageKeys.erase(key);

                auto latestMessage = latestMessagesByKey.find(key);
                if (latestMessage == latestMessagesByKey.end()) {
                    continue;
                }

                message = latestMessage->second;
                latestMessagesByKey.erase(latestMessage);
            }

            lock.unlock();
            
            bool success = sendMessageSafely(message);
            
            if (!success) {
                DebugOut("CRITICAL: Failed to send message, marking client as disconnected");
                clientConnected = false;
                // Don't break here - let the loop condition handle it
            }
            
            lock.lock();
        }
        
        if (!clientConnected) {
            DebugOut("Sender thread: Client disconnected, clearing message queue");
            // Clear remaining messages since client is gone
            while (!messageQueue.empty()) {
                messageQueue.pop();
            }
            while (!latestMessageKeys.empty()) {
                latestMessageKeys.pop();
            }
            queuedLatestMessageKeys.clear();
            latestMessagesByKey.clear();
        }
    }
    
    DebugOut("Sender thread exiting");
}


void AmanServer::enqueueMessage(const std::string& data) {
    static int messageCount = 0;
    messageCount++;
    
    // Only log every 10th message to reduce spam
    bool shouldLog = (messageCount % 10 == 1);
    
    if (shouldLog) {
        DebugOut("enqueueMessage called (#" + std::to_string(messageCount) + "), isRunning: " + 
                 std::to_string(isRunning) + ", clientConnected: " + std::to_string(clientConnected));
    }
    
    if (!isRunning || !clientConnected) {
        if (shouldLog) DebugOut("Message not queued - server not running or client not connected");
        return; // Don't queue messages if not running or no client
    }
    
    try {
        std::lock_guard<std::mutex> lock(queueMutex);
        messageQueue.push(data);
        if (shouldLog) {
            DebugOut("Message queued successfully, queue size: " + std::to_string(messageQueue.size()));
        }
        queueCondition.notify_one();
    } catch (const std::exception& e) {
        DebugOut("Error enqueueing message: " + std::string(e.what()));
    }
}

void AmanServer::enqueueLatestMessage(const std::string& key, const std::string& data) {
    static int messageCount = 0;
    messageCount++;

    bool shouldLog = (messageCount % 10 == 1);

    if (shouldLog) {
        DebugOut("enqueueLatestMessage called (#" + std::to_string(messageCount) + "), key: " + key);
    }

    if (!isRunning || !clientConnected) {
        if (shouldLog) DebugOut("Latest message not queued - server not running or client not connected");
        return;
    }

    try {
        std::lock_guard<std::mutex> lock(queueMutex);
        latestMessagesByKey[key] = data;
        if (queuedLatestMessageKeys.insert(key).second) {
            latestMessageKeys.push(key);
        }
        if (shouldLog) {
            DebugOut("Latest message queued, regular queue size: " + std::to_string(messageQueue.size()) +
                ", latest queue size: " + std::to_string(latestMessageKeys.size()));
        }
        queueCondition.notify_one();
    } catch (const std::exception& e) {
        DebugOut("Error enqueueing latest message: " + std::string(e.what()));
    }
}

