# Auto Test Service

### Prerequisites

- **Java 25** or higher
- **Maven 3.6+**
- **Node.js 22.22.0+**
- **npm 10.9.4+**
- **SQL Server** database instance

### Installation and Running

**Configure environment variables**:
   - Create a `.env` file in the service directory or repository root (use `.env.example` as template)
   - Set required values:
     ```
     DATABASE_URL=jdbc:sqlserver://localhost:1433;databaseName=themajorones
     DATABASE_USERNAME=sa
     DATABASE_PASSWORD=YourPassword123
     CLIENT_ID=your_github_client_id
     CLIENT_SECRET=your_github_client_secret
     DEV_HOSTNAME=http://service-to-url
     ```
