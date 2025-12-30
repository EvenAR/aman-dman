# AMAN-DMAN Common

This module contains shared domain classes and DTOs used by both the backend and client applications.

## Purpose

To avoid duplication and ensure consistency between the client and server, all shared domain objects, value objects, and DTOs are defined here.

## Usage

This module is imported as a dependency by:
- `aman-dman-backend` - Kotlin/Ktor backend server
- `aman-dman-client` - Kotlin/Swing frontend client

## Contents

- Domain models (Aircraft, Airport, Timeline, etc.)
- Value objects
- Shared DTOs for client-server communication
- Common exceptions
