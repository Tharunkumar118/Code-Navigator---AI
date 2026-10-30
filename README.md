# CodeNavigator AI — Intelligent Software Architecture & Code Understanding Platform

CodeNavigator AI is an intelligent software understanding platform designed to help developers, students, and architects understand, analyze, and document complex codebases automatically. By parsing repository source files, it compiles interactive architectural models, exposes API specifications, details relational database entity structures, audits clean-code quality, and identifies security vulnerabilities.

---

## Key Features

1. **Architectural Analysis**: Maps package layouts, computes caller graphs, and generates 6 distinct interactive Mermaid diagrams (Class, Package, Component, Sequence, Use Case, and Deployment layers).
2. **Source Code Metrics**: Scans AST declarations to track average cyclomatic complexity, total classes/methods, and inheritance structures.
3. **API Documentation**: Auto-generates OpenAPI 3.0 specs mapping endpoints, requests, parameters, and controllers.
4. **Database Intelligence**: Scans JPA models to visualize database entity-relationship (ER) diagrams and column metadata catalogs.
5. **Code Quality Audits**: Detects code smells (long methods, complexity overages, naming violations, duplicate lines, unused variables) and updates an interactive dashboard score.
6. **Security Audit**: Identifies critical threats (SQL Injection concatenations, plaintext passwords/tokens, weak JWT secrets, unprotected routes) and provides remediation fixes.
7. **Report Exports**: Consolidates reports for client-side download as Markdown, HTML, JSON datasets, SVGs, or clean print-to-PDF styles.

---

## Tech Stack

* **Backend**: Java 17, Spring Boot, Spring Data JPA, JavaParser AST library, Jackson, H2 Database (In-Memory).
* **Frontend**: HTML5, Vanilla CSS3 (Custom Glassmorphism), Vanilla ES6 JavaScript, Lucide Icons, D3.js (Force-Directed Graph), Mermaid.js (SVG Renderer).

---

## Getting Started

### Prerequisites
* **Java Development Kit (JDK) 17** or higher
* **Apache Maven 3.8+**
* A modern web browser

### 1. Run the Backend Server
Navigate to the backend directory and compile/start the Spring Boot application:
```bash
cd codenavigator-ai/backend
mvn spring-boot:run
```
* The backend API server will start on: **`http://localhost:8081`**
* The H2 Database console is available at `http://localhost:8081/h2-console` (JDBC URL: `jdbc:h2:mem:codenavigator_db`, Username: `sa`, Password: `password`).

### 2. Run the Frontend Dashboard
Navigate to the frontend directory and serve the static files:
```bash
cd codenavigator-ai/frontend
# Using python's built-in server
python -m http.server 8000
```
* Open your browser and navigate to: **`http://localhost:8000`**

---

## 3. Enable the AI Chat Assistant (real AI answers)

The AI Chat feature has **two modes**, and by default it runs in the weaker one:

| Mode | When it's used | What it does |
|---|---|---|
| **OFFLINE** (default, no setup) | No `GEMINI_API_KEY` is configured | A simple keyword matcher. It only recognizes a handful of fixed words (`api`, `database`, `quality`, `security`, `metrics`, `help`). Any question that doesn't contain one of those words falls through to one generic "Codebase Navigator Summary" — which is why it can look like it gives the same answer to everything. |
| **LIVE** (real AI) | `GEMINI_API_KEY` is set | Every prompt is sent to Google's Gemini model together with your project's actual analysis data (metrics, API list, DB schema, quality/security results), and you get a real, unique, context-aware answer per question — like ChatGPT/Gemini/Blackbox. |

**To turn on LIVE mode:**

1. Get a free Gemini API key from **[Google AI Studio](https://aistudio.google.com/apikey)** (sign in with a Google account, click "Create API key").
2. Set it as an environment variable before starting the backend:
   ```bash
   # macOS/Linux
   export GEMINI_API_KEY=your_actual_key_here

   # Windows PowerShell
   $env:GEMINI_API_KEY="your_actual_key_here"
   ```
3. Start the backend as normal (`mvn spring-boot:run`) in the **same terminal** where you set the variable.
4. In the chat UI, each AI reply's author label shows `AI Navigator (LIVE)` once it's working — if it still says `AI Navigator (OFFLINE)`, the key isn't being picked up (see Troubleshooting below).

You never have to touch code to enable this — it's purely an environment variable. If you skip this step, the app still works, just with the limited keyword-based fallback instead of real AI.

### Troubleshooting: chat still says OFFLINE after setting the key
- Confirm the variable is actually set in the terminal you're running `mvn spring-boot:run` from: `echo $GEMINI_API_KEY` (macOS/Linux) or `echo $env:GEMINI_API_KEY` (PowerShell) should print your key, not blank.
- Check the backend console logs right after sending a chat message — any Gemini API failure is now logged with the real HTTP status and response body (e.g., invalid key, quota exceeded), so the log line will tell you exactly what's wrong instead of failing silently.
- Make sure there's no leading/trailing space or quote character accidentally included when you set the key.

---

## Running Tests

To execute the complete unit and integration test suite (covering security scanning, JPA entity parsing, diagram generators, and AST analyzers):
```bash
cd codenavigator-ai/backend
mvn test
```
All **25 unit tests** compile and execute successfully.
