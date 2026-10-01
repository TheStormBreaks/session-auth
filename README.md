# Session Auth

A session-based authentication example split into an independent Spring Boot API and React frontend.

## Architecture

The application separates its user interface from its server-side API. The active UI is a React single-page application; Spring Boot provides REST endpoints and owns authentication and persisted data. The backend does not render the login, signup, or dashboard pages with Thymeleaf, and Angular is not used in this implementation.

### Backend: Spring Boot

- `src/main/java/`: Spring Boot 4 application running on Java 21. Controllers under `controller/` expose `/api/auth/*` and `/api/spn/uploads/*` endpoints; services contain authentication and SPN data operations; repositories and H2 provide persistence.
- The API returns JSON for application data, with binary responses for the CAPTCHA image and Excel workbook export.
- Authentication uses an HTTP session cookie. The frontend includes credentials on API requests so the browser sends that cookie.
- SPN XML parsing starts in the browser. Parsed category metadata and records are sent to the backend in batches; the backend stores them and serves paginated/searchable records and workbook exports.
- The runtime H2 database is file-backed at `data/sessionauth`, so imports remain available after restarting the backend. Test runs use an isolated in-memory database.

### Frontend: React and Vite

- `frontend/`: React 19 application built and served by Vite. It owns login, signup, dashboard, SPN upload, record browsing, and download interactions.
- The frontend calls the backend through `/api/*`. During development, Vite serves the UI on port 5173 and proxies `/api` requests to Spring Boot on port 8080.
- The frontend and backend have separate build/run commands, so the UI can be developed independently while using the Spring Boot API.

### Request Flow

1. The browser renders the React application served by Vite.
2. React sends authenticated requests to the Spring Boot REST API using the session cookie.
3. Spring Boot validates the session, applies application rules, reads or writes H2 data, and returns JSON or a file response.
4. React renders the API response and handles user interactions; the backend does not return server-rendered page templates.

## Run locally

Prerequisites: JDK 21, Node.js 20.19+ or 22.12+, and npm.

Start the backend in one terminal:

```powershell
./gradlew.bat bootRun
```

Start the frontend in another terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The frontend dev server proxies API calls to the backend on port 8080.

Run backend tests with `./gradlew.bat test`. Build the frontend with `npm run build` from `frontend/`.

## API

- `GET /api/auth/captcha`: CAPTCHA image; the challenge is stored in the HTTP session.
- `POST /api/auth/signup`: validates CAPTCHA and creates the user; returns `201` with a message.
- `POST /api/auth/login`: validates credentials and CAPTCHA; establishes a session and returns the username.
- `GET /api/auth/me`: returns the authenticated username or `401`.
- `POST /api/auth/extend-session`: rotates the session ID/cookie, resets the 15-minute timeout, or returns `401`.
- `POST /api/auth/logout`: invalidates the session.
- `POST /api/spn/uploads`: creates an import and category index for the signed-in user.
- `POST /api/spn/uploads/{id}/records`: stores a batch of parsed category records.
- `POST /api/spn/uploads/{id}/complete`: verifies the row counts and makes the import available.
- `GET /api/spn/uploads/latest`: returns the user's latest completed import.
- `GET /api/spn/uploads/{id}/records?categoryKey=...&page=0`: returns 10 database records for the selected category/page as `{ id, values }` items; optional `query` searches stored record content.
- `POST /api/spn/uploads/{id}/records/new?categoryKey=...`: adds a record to a completed upload and updates its category and upload counts.
- `PUT /api/spn/uploads/{id}/records/{recordId}`: replaces a record's field values and refreshes its searchable content.
- `DELETE /api/spn/uploads/{id}/records/{recordId}`: deletes a record and updates its category and upload counts.
- `GET /api/spn/uploads/{id}/excel`: streams all parsed categories and records as an `.xlsx` workbook download.

Login/signup request JSON:

```json
{
  "username": "example",
  "password": "at-least-eight-characters",
  "captcha": "AB12CD"
}
```

Session cookie name: `SESSION_AUTH_ID`. Database connection settings are in `src/main/resources/application.properties`.
