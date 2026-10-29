# CineReserve — DA3 Project Report

## 1. Project overview

CineReserve is a cinema discovery and reservation prototype. Customers can browse movies and showtimes, inspect seat availability, create an account, book a seat, review their bookings, and cancel a confirmed booking. The project keeps its existing HTML, CSS, and JavaScript interface and uses a small Java HTTP API backed by CockroachDB.

## 2. Technology stack

- Frontend: HTML5, CSS3, vanilla JavaScript, Bootstrap components
- Backend: Java 21 and the JDK `HttpServer` API
- Database: CockroachDB using its PostgreSQL wire protocol
- JDBC: PostgreSQL JDBC driver

## 3. Database design

The relational schema contains `Users`, `Movies`, `Theatres`, `Screens`, `Seats`, `Movie_Shows`, `Show_Seats`, `Bookings`, `Tickets`, `Payments`, and `User_Sessions`. Primary and foreign keys enforce relationships. Unique constraints prevent duplicate account emails, duplicate seats per screen/show, duplicate tickets, and duplicate payment references. Checks constrain seat, booking, ticket, payment, price, and duration values.

`vw_show_availability` joins show, movie, screen, theatre, and seat data to report total and available seats. Indexes support common foreign-key joins, showtime reads, booking history, and availability lookups.

The database migration preserves existing rows. `schema.sql` is repeatable, and `seed.sql` inserts missing sample records by natural keys. The seed also aligns identity sequences after older scripts that supplied explicit IDs. Neither script deletes rows; the seed updates the demo booking's seat status to keep it consistent with that sample booking.

## 4. Application behavior and persistence

- Account registration hashes passwords with PBKDF2-HMAC-SHA256 and stores the account in CockroachDB.
- Login creates a seven-day session stored in `User_Sessions`; the HTTP-only cookie survives page refreshes and the server can resolve it from the database.
- Movie, showtime, and seat views read directly from CockroachDB.
- Booking runs in one database transaction: it locks and claims an available seat, creates a confirmed booking, ticket, and simulated successful payment, and commits together. A failed step rolls back the transaction.
- Booking history is scoped to the authenticated user.
- Cancellation updates booking/ticket/payment state in one transaction and makes the seat available again. Records remain for history.

## 5. API summary

| Method | Endpoint | Operation |
| --- | --- | --- |
| GET | `/api/health` | Database health |
| GET | `/api/movies` | Read movies |
| GET | `/api/shows` | Read shows |
| GET | `/api/seats?showId=…` | Read show seats |
| POST | `/api/auth/register` | Create account |
| POST | `/api/auth/login` | Create session |
| GET | `/api/auth/me` | Read current account |
| POST | `/api/auth/logout` | Delete session |
| POST | `/api/bookings` | Create booking, ticket, and payment |
| GET | `/api/bookings` | Read booking history |
| DELETE | `/api/bookings?bookingId=…` | Cancel a booking |

## 6. Reports

`database/reports.sql` provides:

1. Movie showtimes and seat availability.
2. Booking details with payment status.
3. Movie-level bookings and successful revenue.
4. Successful payment count and total.

## 7. Verification record

The following checks were completed locally:

- Java backend compiled with JDK 21 and PostgreSQL JDBC.
- Frontend JavaScript passed Node syntax validation.
- CockroachDB schema applied to the existing `cinereserve` database.
- The repeatable seed ran twice; the second run inserted no duplicate records.
- Reporting queries executed successfully.
- Browser flow passed against the local site: database movies/showtimes/seats loaded, a test account registered and logged in, a seat booking appeared in My Bookings, cancellation updated the visible history, and sign out restored the logged-out UI.
- Direct CockroachDB inspection confirmed the test booking, ticket, payment, and seat statuses (`CANCELLED`, `CANCELLED`, `REFUNDED`, `AVAILABLE`) before its test-only records were removed.
- API smoke flow also passed session restore and logout. Test-only accounts and dependent rows were cleaned up; original and seeded database records were preserved.

### Fresh end-to-end verification (2026-10-30)

- Confirmed Java 21 is installed at `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` and CockroachDB, the Java API, and the static frontend were already listening on ports 26257, 8081, and 5500 respectively.
- Rebuilt the Java backend with `./backend/build.sh`; `node --check js/app.js` passed. A scan of Java and SQL sources found no Oracle JDBC references or Oracle-only SQL constructs. All Java database access goes through `DatabaseConnection` and `jdbc:postgresql:` using the PostgreSQL JDBC driver.
- The live API health endpoint returned `database: connected`; the live frontend returned HTTP 200 and movie listings were read from the API.
- Fixed `/api/shows` to omit past showtimes. After rebuilding and starting the new backend, a date assertion passed: all 3 returned shows were today or later; past database rows were retained.
- Through the browser website, created a uniquely tagged temporary account, logged in, browsed a database-backed movie/show/seat, and booked show 4, seat A2. CockroachDB showed booking 5 as `CONFIRMED`, ticket `VALID`, simulated payment `SUCCESS`, amount 180.00, and seat `BOOKED`.
- Canceled that booking through the website. CockroachDB showed booking and ticket `CANCELLED`, payment `REFUNDED`, and seat `AVAILABLE`.
- Removed only the temporary account and its dependent session, booking, ticket, and payment rows. Final checks showed zero rows for the test account/bookings, the seat still `AVAILABLE`, and the existing baseline totals remained 1 user and 2 bookings.
- Confirmed the database, backend, and frontend processes remained active after verification.
- Validated the macOS launcher syntax with `bash -n`. The first live run exposed a strict-shell pipeline issue in the frontend check; that was fixed. A second live run succeeded, reused the existing CockroachDB/API/frontend, skipped schema/seed because the schema was present, and opened the website in Chrome. A final read-only query still showed 1 user and 2 bookings.
- Validated the macOS launcher through Terminal: it reused CockroachDB and the frontend, started the rebuilt API after it had been stopped, opened the website in Chrome, and the API remained listening afterward.
- Final live GET checks passed for `/api/health`, `/api/movies`, `/api/shows`, and `/api/seats?showId=4`; returned values matched the database, including seat A2 as `AVAILABLE`.

## 8. Run instructions

Follow [`README.md`](README.md) to create/use the local `cinereserve` database, apply the schema and seed, build/run the Java API, and serve the frontend.

## 9. Scope notes and unverified features

The existing customer UI supports account creation, sign-in, catalog reads, show/seat reads, booking creation/history, booking state updates through cancellation, and soft deletion through that cancellation flow. It does not provide admin create/update/delete screens for movies, theatres, shows, or seats; those catalog records are read-only in the app. There is no hard-delete action for bookings or accounts. Theatre cards are static sample content rather than database reads. Payments are simulated and do not move money. The sample demo account has a placeholder password hash; users should register through the UI for a working account. The CockroachDB insecure mode and default root connection are local-development settings only.

Browser verification used the existing frontend and backend against local CockroachDB. Other browsers, mobile breakpoints, simultaneous booking contention, and failure recovery during a database outage were not separately exercised.

The launcher was tested with the database and frontend already running and the API stopped; it successfully started the rebuilt API. Starting CockroachDB from a stopped state, starting the frontend from a stopped state, and the occupied-port error path were not exercised, to avoid disrupting or replacing unrelated services.
