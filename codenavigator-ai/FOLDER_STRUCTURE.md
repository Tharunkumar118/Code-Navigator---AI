# CodeNavigator AI — Project Folder Structure

Below is the directory tree of the workspace:

```text
codenavigator-ai-module1-3/
└── codenavigator-ai/
    ├── README.md
    └── backend/
        ├── pom.xml
        └── src/
            ├── main/
            │   ├── java/
            │   │   └── com/
            │   │       └── codenavigator/
            │   │           └── ai/
            │   │               ├── CodeNavigatorAiApplication.java
            │   │               ├── config/
            │   │               │   ├── DataSeeder.java         # Seed default database roles
            │   │               │   ├── SecurityConfig.java     # Web security configuration
            │   │               │   └── WebConfig.java          # CORS setup config
            │   │               ├── controller/
            │   │               │   ├── AuthController.java     # Login/Register endpoints
            │   │               │   ├── DashboardController.java# Admin & Student stats
            │   │               │   ├── HealthController.java   # App/Database health check
            │   │               │   └── UserController.java     # User account profiles
            │   │               ├── dto/                        # Request/Response payloads
            │   │               │   ├── ActivityLogDto.java
            │   │               │   ├── AdminDashboardResponse.java
            │   │               │   ├── ApiResponse.java
            │   │               │   ├── AuthResponse.java
            │   │               │   ├── LoginRequest.java
            │   │               │   ├── RegisterRequest.java
            │   │               │   ├── RepositorySummaryDto.java
            │   │               │   ├── StudentDashboardResponse.java
            │   │               │   └── UserProfileResponse.java
            │   │               ├── entity/                     # JPA Model Mapping classes
            │   │               │   ├── ActivityLog.java
            │   │               │   ├── AnalysisRecord.java
            │   │               │   ├── AnalysisStatus.java
            │   │               │   ├── AnalysisType.java
            │   │               │   ├── CodeRepository.java
            │   │               │   ├── ERole.java
            │   │               │   ├── Report.java
            │   │               │   ├── RepoStatus.java
            │   │               │   ├── Role.java
            │   │               │   └── User.java
            │   │               ├── exception/                  # App validation handlers
            │   │               │   ├── EmailAlreadyExistsException.java
            │   │               │   ├── GlobalExceptionHandler.java
            │   │               │   └── ResourceNotFoundException.java
            │   │               ├── repository/                 # Database interaction interfaces
            │   │               │   ├── ActivityLogRepository.java
            │   │               │   ├── AnalysisRecordRepository.java
            │   │               │   ├── CodeRepositoryRepository.java
            │   │               │   ├── ReportRepository.java
            │   │               │   ├── RoleRepository.java
            │   │               │   └── UserRepository.java
            │   │               ├── security/                   # Token verification filter layers
            │   │               │   ├── JwtAuthFilter.java
            │   │               │   ├── JwtUtils.java
            │   │               │   ├── UserDetailsImpl.java
            │   │               │   └── UserDetailsServiceImpl.java
            │   │               └── service/                    # Business process implementation
            │   │                   ├── AuthService.java
            │   │                   ├── DashboardService.java
            │   │                   └── impl/
            │   │                       ├── AuthServiceImpl.java
            │   │                       └── DashboardServiceImpl.java
            │   └── resources/
            │       ├── application.yml                         # Application properties
            │       ├── application-dev.yml                     # Development MySQL settings
            │       ├── application-h2.yml                      # Development H2 in-memory settings
            │       └── application-prod.yml                    # Production active settings
            └── test/                                           # Application tests
                ├── java/
                │   └── com/
                │       └── codenavigator/
                │           └── ai/
                │               ├── CodeNavigatorAiApplicationTests.java
                │               └── controller/
                │                   ├── AuthControllerTest.java
                │                   ├── DashboardControllerTest.java
                │                   └── HealthControllerTest.java
                └── resources/
                    └── application-test.yml                    # Setup profile for tests
```
