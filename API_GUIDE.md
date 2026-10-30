# API Routing Specification Guide

The backend services run on: **`http://localhost:8081/api/v1`**. All API requests and responses utilize standard JSON payloads.

---

## 1. Authentication Endpoints

### 1.1 User Registration
* **Endpoint**: `POST /auth/register`
* **Request Payload**:
```json
{
  "email": "developer@example.com",
  "password": "SecurePassword123",
  "fullName": "Senior Developer"
}
```
* **Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "accessToken": "eyJhbGciOi..."
  }
}
```

### 1.2 User Login
* **Endpoint**: `POST /auth/login`
* **Request Payload**:
```json
{
  "email": "developer@example.com",
  "password": "SecurePassword123"
}
```
* **Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Authentication successful",
  "data": {
    "accessToken": "eyJhbGciOi..."
  }
}
```

---

## 2. Repository Endpoints

### 2.1 Register & Clone Repository
* **Endpoint**: `POST /repositories`
* **Headers**: `Authorization: Bearer <JWT_TOKEN>`
* **Request Payload**:
```json
{
  "repoUrl": "https://github.com/opsmind/spring-demo"
}
```
* **Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Repository cloning process successfully initialized",
  "data": {
    "id": 1,
    "repoName": "spring-demo",
    "repoUrl": "https://github.com/opsmind/spring-demo",
    "status": "CLONING"
  }
}
```

### 2.2 List Cloned Repositories
* **Endpoint**: `GET /repositories`
* **Headers**: `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "repoName": "spring-demo",
      "repoUrl": "https://github.com/opsmind/spring-demo",
      "status": "COMPLETED",
      "primaryLanguage": "Java"
    }
  ]
}
```

---

## 3. Analysis Endpoints

### 3.1 Submit Analysis Request
* **Endpoint**: `POST /analyses`
* **Headers**: `Authorization: Bearer <JWT_TOKEN>`
* **Request Payload**:
```json
{
  "repositoryId": 1,
  "type": "CODE_QUALITY"
}
```
* **Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Analysis successfully scheduled",
  "data": {
    "id": 4,
    "status": "RUNNING",
    "type": "CODE_QUALITY"
  }
}
```

### 3.2 Poll Analysis Status & Results
* **Endpoint**: `GET /analyses/{id}`
* **Headers**: `Authorization: Bearer <JWT_TOKEN>`
* **Success Response (200 OK - COMPLETED)**:
```json
{
  "success": true,
  "data": {
    "id": 4,
    "status": "COMPLETED",
    "type": "CODE_QUALITY",
    "result": {
      "qualityScore": 92,
      "issues": [
        {
          "filePath": "src/main/java/com/test/UserController.java",
          "line": 45,
          "ruleName": "Long Method smell",
          "severity": "WARNING",
          "description": "Method 'getUserData()' contains 38 lines. Consider splitting."
        }
      ]
    }
  }
}
```
