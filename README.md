# CineReserve

CineReserve is a cinema discovery and ticket booking project with a plain HTML/CSS/JavaScript frontend, a Java HTTP API, and CockroachDB persistence. The existing visual design and movie/theatre discovery remain in place. Account registration, login, seat selection, booking history, and cancellation now use the database-backed API.

## Requirements

- CockroachDB running locally on `localhost:26257`
- Java 21
- `curl` (the build script downloads the PostgreSQL JDBC driver when it is missing)
- Python 3 for the static web server

The local insecure CockroachDB configuration is intended only for development. It has no authentication or TLS; keep its ports bound to localhost.

## Quick launch on macOS

For tomorrow's launch, double-click **`start-cinereserve.command`** in Finder. It starts or reuses CockroachDB, checks the existing `cinereserve` schema, starts or reuses the Java API and frontend, then opens the site. It does not stop processes that it did not start. If one of the expected ports belongs to another/unhealthy service, it stops with a message instead of killing that process.

The launcher requires Java 21, CockroachDB, Python 3, `curl`, and `lsof`. Java 21 is already installed on this Mac. If needed on another Mac, install Temurin 21 with Homebrew:

```sh
brew install --cask temurin@21
```

If double-clicking is blocked by macOS, open Terminal, change to the project folder, and run the launcher:

```sh
cd ~/Downloads/CineReserve-DA3/CineReserve-main
./start-cinereserve.command
```

Services write logs to `~/Library/Logs/CineReserve/`. The launcher reuses an existing CockroachDB node and store at `~/cockroach-data`; it does not reset or delete that store. It applies the additive schema/seed only if the app schema is missing. Existing data is preserved.

Open [http://127.0.0.1:5500](http://127.0.0.1:5500) if the browser did not open automatically. Register or log in, choose a movie/show and an available seat, then select **Book selected seat**. Verify the booking in **My Bookings**; cancel it to test cancellation and seat release. Payments are simulated.

### Manual start (if needed)

Check CockroachDB first:

```sh
lsof -nP -iTCP:26257 -sTCP:LISTEN
```

If it is stopped, start it in Terminal 1. This uses the existing local store:

```sh
cd ~
cockroach start-single-node --insecure --listen-addr=localhost:26257 --http-addr=localhost:8080 --store=cockroach-data
```

Start the API in Terminal 2:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
cd ~/Downloads/CineReserve-DA3/CineReserve-main
./backend/run.sh
```

Start the frontend in Terminal 3:

```sh
cd ~/Downloads/CineReserve-DA3/CineReserve-main
python3 -m http.server 5500 --bind 127.0.0.1
```

## Database setup

Create the app database if it does not already exist:

```sh
cockroach sql --insecure --host=localhost:26257 --execute="CREATE DATABASE IF NOT EXISTS cinereserve;"
```

From this directory, apply the schema and sample data:

```sh
cockroach sql --insecure --host=localhost:26257 --database=cinereserve --file=database/schema.sql
cockroach sql --insecure --host=localhost:26257 --database=cinereserve --file=database/seed.sql
```

Both scripts can be rerun. Schema objects use `IF NOT EXISTS`; the seed checks natural keys, synchronizes legacy identity sequences, and never deletes rows. It adds missing sample records and keeps the demo booking's seat marked as booked.

Run the optional reports with:

```sh
cockroach sql --insecure --host=localhost:26257 --database=cinereserve --file=database/reports.sql
```

## Start the app

Terminal 1, from this directory:

```sh
./backend/run.sh
```

The script compiles the Java sources and starts the API on `http://localhost:8081`. The database connection defaults to the local insecure CockroachDB settings:

- URL: `jdbc:postgresql://localhost:26257/cinereserve?sslmode=disable`
- User: `root`
- Password: empty

Override these with `CINERESERVE_DB_URL`, `CINERESERVE_DB_USER`, and `CINERESERVE_DB_PASSWORD` when needed. To compile without launching the API, run `./backend/build.sh`.

Terminal 2, from this directory:

```sh
python3 -m http.server 5500 --bind 127.0.0.1
```

Open [http://127.0.0.1:5500](http://127.0.0.1:5500). Use the same host consistently so the session cookie works across the local frontend and API.

Create a new CineReserve account from the Register section. The sample demo account has a placeholder password hash and is not a login account.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | API and DB health |
| GET | `/api/movies` | Read movie catalog |
| GET | `/api/shows` | Read upcoming showtimes and availability |
| GET | `/api/seats?showId=…` | Read seats for a show |
| POST | `/api/auth/register` | Create an account |
| POST | `/api/auth/login` | Start a database-backed session |
| GET | `/api/auth/me` | Read the signed-in account |
| POST | `/api/auth/logout` | End a session |
| POST | `/api/bookings` | Book an available seat; writes booking, ticket, and simulated payment atomically |
| GET | `/api/bookings` | Read the signed-in user’s bookings |
| DELETE | `/api/bookings?bookingId=…` | Cancel a booking and release its seat |

Cancellation is a soft state change: booking and ticket records remain for history, and the simulated successful payment is marked refunded. Payments are demonstrations only; no real payment provider is connected.

## Project map

- `index.html`, `css/style.css`, `js/app.js`: existing frontend and database-backed integration
- `backend/src/`: Java API and PostgreSQL JDBC connection
- `backend/pom.xml`: PostgreSQL JDBC dependency for Maven-based setups
- `database/schema.sql`: CockroachDB schema, indexes, sessions, and availability view
- `database/seed.sql`: repeatable, additive demo seed
- `database/reports.sql`: project reporting queries
- `DA3_REPORT.md`: submission-oriented project and verification report

## Verification

Verified locally on 2026-10-30: Java 21 is installed; `./backend/build.sh`, `node --check js/app.js`, and `bash -n start-cinereserve.command` pass. The launcher was opened through macOS Terminal: it reused the database and frontend, started the rebuilt backend, and opened the website. Live API checks passed for health, movies, upcoming shows, and seats; all three returned show dates were today or later. A browser test registered and logged into a unique temporary account, booked show 4 seat A2, and canceled it. CockroachDB confirmed booking/ticket/payment/seat writes and cancellation state. Only the temporary account and its dependent records were removed; existing totals remained 1 user and 2 bookings. There are no Oracle JDBC references or Oracle-specific SQL constructs in the Java/database sources. Starting CockroachDB or the frontend from stopped states and the launcher's occupied-port error path were not exercised.

The current customer UI has no admin catalog create/update/delete screens, no account or booking hard-delete action, and no real payment provider. Theatre cards are static sample content. Cancellation is a persisted status update and soft deletion.
