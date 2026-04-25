#pragma once

#include <map>
#include <queue>
#include <set>
#include <thread>
#include <mutex>
#include <atomic>
#include <condition_variable>
#include <string>
#include <functional>
#include <winsock2.h>

#include "ServerEventsHandler.h"

class AmanServer : public ServerEventsHandler {
public:
    AmanServer();
    ~AmanServer();

protected:
    void startServer();
    void stop();
    void enqueueMessage(const std::string& data);
    void enqueueLatestMessage(const std::string& key, const std::string& data);
    void drainInboundMessages();

private:
    void serverLoop();
    void handleClientConnection();
    void configureClientSocket();
    void enqueueInboundMessage(const std::string& data);
    void senderThreadLoop();
    bool sendMessageSafely(const std::string& message);

    std::thread serverThread;
    std::thread senderThread;
    std::atomic<bool> isRunning;
    std::atomic<bool> clientConnected;
    SOCKET listenSocket;
    SOCKET clientSocket;

    std::queue<std::string> messageQueue;
    std::queue<std::string> latestMessageKeys;
    std::set<std::string> queuedLatestMessageKeys;
    std::map<std::string, std::string> latestMessagesByKey;
    std::mutex queueMutex;
    std::condition_variable queueCondition;

    std::queue<std::string> inboundMessageQueue;
    std::mutex inboundQueueMutex;
};

