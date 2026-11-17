# EquiFlow Microservices

EquiFlow is a campus equipment lending application with a React frontend and Spring Boot services. Students can reserve equipment, and administrators can manage lending and maintenance.

The Windows H2 demo uses port 8086. The Docker deployment uses port 8087 on Windows and macOS.

## Run with Docker Desktop on Windows or macOS

Install and open Docker Desktop. On Windows, use Linux containers with the WSL 2 backend. On macOS, install the version for Apple silicon or Intel.

Open a terminal in [RunningByDocker](RunningByDocker). On Windows, run this command in PowerShell.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Start-Portable.ps1
```

On macOS, run this command in Terminal.

```sh
sh ./Start-Portable.sh
```

The launch script creates `.env.portable` with three random secrets on first use. It reuses that file on later starts so MySQL retains access to its data volume. The script builds the application image and waits for the Compose services to start.

The first build needs internet access. A fresh MySQL volume is initialized automatically from the [SQL dump](backend/database/equiflow-microservices.sql). Open [http://127.0.0.1:8087](http://127.0.0.1:8087) and select a built-in demo account.

The stop command keeps the MySQL and Kafka data volumes.

```sh
docker compose --env-file .env.portable -f compose.yaml -f compose.portable.yaml stop
```

For later starts, run the same launch script.

## Run with H2 on Windows

Install Java 21 and add it to `PATH`. If the seven service JARs are already in `runtime/services/`, proceed to the launcher below.

To build missing JARs from source, install JDK 21, Maven, and Node.js 22 with npm, and add their command-line tools to `PATH`. Stop the H2 demo before rebuilding its JARs. Open PowerShell in the project root and run the build.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Build.ps1
```

Once the JARs are in place, double-click [Start-Demo.cmd](RunningByH2/Start-Demo.cmd). Open [http://127.0.0.1:8086](http://127.0.0.1:8086) and select a built-in demo account. The demo uses local H2 databases, creates sample records for the current date, and saves changes under `.local/`. Double-click [Stop-Demo.cmd](RunningByH2/Stop-Demo.cmd) to stop the application.

The first source build uses Maven Central and npm. If the runtime is missing Kafka or Zipkin, startup retrieves those components from Maven Central. With all runtime components in place, the demo can start offline with Java 21 on `PATH`.

### Access from another computer

On a trusted network, stop the running demo before changing the listen address. From the project root, run the following command.

```powershell
.\RunningByH2\Start-Demo.cmd -ListenAddress 0.0.0.0
```

On the other computer, open `http://HOST_IP:8086`, replacing `HOST_IP` with the Windows host's IP address. Allow inbound TCP traffic on port 8086 through the host firewall if needed.

## Run Docker and H2 at the same time on Windows

Follow the Docker Desktop steps and open [http://127.0.0.1:8087](http://127.0.0.1:8087). Then start the H2 demo and open [http://127.0.0.1:8086](http://127.0.0.1:8086). Either instance can be stopped independently using its own stop command.

Each instance uses its own database. The H2 demo stores data in `.local/services/`, and the Docker Desktop deployment stores MySQL data in a Docker volume. To sign in to both instances at the same time, use separate browser profiles so that one login cookie does not overwrite the other.

## Run with Docker and Windows MySQL

Run a Linux Docker Engine with Docker Compose inside a WSL 2 distribution with NAT networking. Install MySQL Server 8.0 or 8.4 on Windows.

From PowerShell in the project root, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\RunningByDocker\Prepare-DockerMySql.ps1
```

This starts a dedicated Windows MySQL instance on port 3316 and restores the four databases from the bundled SQL dump on first use. The instance binds to Windows loopback and the WSL gateway. The script writes the TLS database connection settings and the internal service key to `.local/docker-mysql.env`.

Open RunningByDocker in WSL and use the Docker client for that distribution.

```sh
docker compose --env-file ../.local/docker-mysql.env build registry
docker compose --env-file ../.local/docker-mysql.env up -d --no-build --wait
```

Open [http://127.0.0.1:8087](http://127.0.0.1:8087). To stop the containers and retain the database and Kafka data, run `docker compose --env-file ../.local/docker-mysql.env stop` in RunningByDocker.

For the dedicated local WSL Docker engine, run [Compose-Wsl.ps1](RunningByDocker/Compose-Wsl.ps1) from PowerShell with `-Action Start`, `-Action Status`, or `-Action Stop`. Add `-Build` to rebuild the image after editing the source. The helper accepts `-Distribution` and `-EngineDirectory` for other WSL Docker engine locations.

## Restore the MySQL database

Install MySQL Server 8.0 or 8.4. The [SQL dump](backend/database/equiflow-microservices.sql) contains the schema and data for all four service databases. Build the application if needed, then run the following command from the project root to restore the dump into empty service databases.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start.ps1 -Profile mysql -RestoreDump
```

| Option | Purpose |
| --- | --- |
| `-MySqlHome "C:\path\to\mysql"` | Use a custom MySQL installation. |
| `-MySqlRuntimeDirectory "C:\path\to\new-data"` | Use a new managed data directory. The path must contain only ASCII characters. |
| `-MySqlPort 3316` | Set the managed MySQL port. The default is `3316`. |

For later starts, use the same MySQL options with `-Profile mysql` and omit `-RestoreDump`. After stopping the application, stop the managed MySQL server with the following command.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Stop-MySql.ps1
```

## Open in IntelliJ IDEA

Open the project root and select JDK 21. IntelliJ IDEA imports the Maven modules from [backend/pom.xml](backend/pom.xml). After building the application, use the **EquiFlow Backend** run configuration to start the MySQL profile.

## Develop the frontend

Start the H2 demo so the Vite development server can reach the API on port 8086. In a separate PowerShell window, run the following commands from the project root.

```powershell
Set-Location .\frontend
npm ci
npm run dev
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173). Vite forwards `/api` requests to the gateway.
