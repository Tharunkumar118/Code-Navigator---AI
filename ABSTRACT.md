# Project Abstract: CodeNavigator AI

### Project Title
**CodeNavigator AI — Intelligent Software Architecture & Code Understanding Platform**

---

## 1. Project Abstract

Modern software development often involves navigating highly complex, legacy, or large-scale codebases, posing a significant challenge for software engineers, architects, and technical mentors. Traditional static analysis tools focus on linting rules but fail to provide a unified, intuitive understanding of system design, API routes, database models, and code quality. **CodeNavigator AI** solves this problem by offering an intelligent, automated code understanding platform that parses repositories to construct interactive system maps and reports.

By compiling Abstract Syntax Trees (AST) using JavaParser and scanning multi-language code structures (Java, Python, JS), the platform automatically maps nested package trees, builds dependency graphs, and generates 6 distinct dynamic Mermaid diagrams (Class, Package, Component, Sequence, Use Case, and Deployment models). In addition, CodeNavigator AI parses JPA annotations to generate Entity-Relationship (ER) schemas, extracts REST mapping definitions to build complete OpenAPI 3.0 API specifications, and audits code quality metrics (cyclomatic complexity, long methods, naming rules). It also scans security vulnerabilities (SQL Injection risks, hardcoded credentials, weak JWT configuration parameters, and open routes) to provide structured remediation fixes. With client-side compiling for Markdown specs, HTML dashboards, JSON datasets, D3 SVGs, and print-ready PDF styling, CodeNavigator AI provides a modern, production-ready solution to accelerate software onboarding, auditing, and architectural documentation.

---

## 2. Key Highlights (15 Points)

1. **Intelligent AST Parsing**: Leverages JavaParser to extract abstract syntax trees for deep parsing of classes, inheritance systems, field models, and method metrics.
2. **6-Layer Mermaid Visualizer**: Automatically generates Class, Package, Component, Sequence, Use Case, and Deployment schemas using Mermaid.js.
3. **ER Database Schema Engine**: Scans JPA mappings to construct relational Entity-Relationship diagrams and maps metadata constraints (PKs, FKs, nulls).
4. **Auto OpenAPI 3.0 Generation**: Auto-compiles full OpenAPI specifications containing endpoint pathways, HTTP routes, controller classes, and return parameters.
5. **AST Code Quality Audits**: Detects code smells (long methods, complex logic flows, naming convention violations, duplicate lines, unused variables) to calculate a quality score out of 100.
6. **Remediation Security Scanner**: Scans codebases for SQL Injection string concatenations, weak JWT keys, unprotected routes, and hardcoded credentials.
7. **Consolidated Spec Exports**: Compiles and downloads comprehensive specification reports as clean Markdown files or presentation-ready HTML.
8. **Scalable Vector Image Exports**: Converts active D3 force-directed graphs and Mermaid diagrams into download-ready SVG files.
9. **PDF Page Printing Engine**: Configures customized print layouts (`@media print`) that hide menus/inputs to export reports as physical print sheets cleanly.
10. **Interactive D3 Mappings**: Visualizes class dependency linkages using a dynamic D3.js force-directed network layout.
11. **Asynchronous Analysis Pipeline**: Executes compute-heavy scans inside background worker threads, preventing REST controller timeouts.
12. **JWT Security Protocols**: Secures backend service controllers using JSON Web Tokens (JWT) for user authentication.
13. **Modern CSS Glassmorphism**: Provides a premium dark-themed web dashboard with responsive grid layouts, animations, and progress gauges.
14. **In-Memory SQL Database**: Integrates an H2 Database to store user credentials, repository listings, and historical analysis scan records.
15. **Zoom-Scale Layout Canvas**: Implements a diagram controls toolbar with Zoom In, Zoom Out, and Reset buttons supporting SVG transformations up to 300%.
