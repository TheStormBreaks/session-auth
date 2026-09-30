# Session Auth

A session-based authentication example split into an independent Spring Boot API and React frontend.

## Architecture

- `src/`: Spring Boot 4 backend, Spring Data JPA, H2, validation, BCrypt, and servlet sessions.
- `frontend/`: React 19 and Vite application. It owns login, signup, and dashboard presentation.
- The browser talks to `/api/auth/*`. Vite proxies `/api` to `http://localhost:8080` during development.
- The dashboard parses `.spn`/XML files locally, saves categories and parsed records to H2 in batches, and loads searchable pages of 10 records at a time. Record inspection shows all nested fields.
- The runtime H2 database is file-backed at `data/sessionauth`, so imports remain available after restarting the backend. Test runs use an isolated in-memory database.

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
- `GET /api/spn/uploads/{id}/records?categoryKey=...&page=0`: returns 10 database records for the selected category/page; optional `query` searches stored record content.

Login/signup request JSON:

```json
{
  "username": "example",
  "password": "at-least-eight-characters",
  "captcha": "AB12CD"
}
```

Session cookie name: `SESSION_AUTH_ID`. Database connection settings are in `src/main/resources/application.properties`.
