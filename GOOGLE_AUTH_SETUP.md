# Google Authentication Setup Guide
### Bharat Railways Reservation & Route Management System

This project supports **Dual-Mode Google Authentication**:
1. **Zero-Setup Out-of-the-Box Mode**: Self-contained local Google OAuth simulator with authentic Google-branded account selection card (Kushal Sindhe, Aarav Sharma, Priya Patel, or custom Google email). Works offline, requires zero API keys.
2. **Live Production Mode**: Connects directly to Google Identity Services (GIS) and Google Cloud OAuth 2.0 with One Tap & account dialogs using your Google Cloud Client ID.

---

## Instructions for Applying Live Google OAuth 2.0

### Step 1: Create a Project in Google Cloud Console
1. Visit the [Google Cloud Console](https://console.cloud.google.com/).
2. Log in with your Google account.
3. Click the project dropdown at the top and select **NEW PROJECT**.
4. Name the project `Bharat-Railways` and click **CREATE**.

---

### Step 2: Configure the OAuth Consent Screen
1. In the left navigation menu, go to **APIs & Services** > **OAuth consent screen**.
2. Select **External** (or **Internal** if using Google Workspace) and click **CREATE**.
3. Fill in the required application details:
   - **App name**: `Bharat Railways`
   - **User support email**: Select your email address.
   - **Developer contact information**: Enter your email address.
4. Click **SAVE AND CONTINUE**.
5. Under **Scopes**, ensure the standard openid scopes are present:
   - `.../auth/userinfo.email`
   - `.../auth/userinfo.profile`
   - `openid`
6. Click **SAVE AND CONTINUE**.
7. Under **Test users**, click **ADD USERS** and add the Gmail addresses you will use for testing.
8. Click **SAVE AND CONTINUE** > **BACK TO DASHBOARD**.

---

### Step 3: Create OAuth 2.0 Web Client Credentials
1. In the left navigation menu, go to **APIs & Services** > **Credentials**.
2. At the top, click **+ CREATE CREDENTIALS** and select **OAuth client ID**.
3. Under **Application type**, select **Web application**.
4. Set the **Name** to `Bharat Railways Web Client`.
5. Under **Authorized JavaScript origins**, click **+ ADD URI** and add:
   - `http://localhost:8080`
   - `http://127.0.0.1:8080`
6. Under **Authorized redirect URIs**, click **+ ADD URI** and add:
   - `http://localhost:8080`
   - `http://127.0.0.1:8080`
7. Click **CREATE**.
8. A modal will pop up displaying your **Client ID** (e.g. `123456789012-abcdefghijklmnopqrstuvwx.apps.googleusercontent.com`). Copy this Client ID!

---

### Step 4: Apply Your Client ID to the Project
1. Open [`web/index.html`](file:///web/index.html) in your editor.
2. Navigate to around line 3053 (under `/* Google Authentication & Google Identity Services (GIS) */`):
   ```javascript
   // Replace this placeholder:
   const GOOGLE_CLIENT_ID = "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com";
   
   // With your actual Google Cloud Client ID:
   const GOOGLE_CLIENT_ID = "123456789012-abcdefghijklmnopqrstuvwx.apps.googleusercontent.com";
   ```
3. Save the file.

---

### Step 5: Test Live Google Authentication
1. Ensure the web server daemon is running on port 8080:
   ```powershell
   java -cp bin com.railway.Main --web-only
   ```
2. Open your browser and navigate to:
   ```
   http://localhost:8080/
   ```
3. Click the **"Sign in with Google"** button or the top **"🔒 Sign In / Register"** chip.
4. The official Google Identity Services popup will prompt you to select your Google Account.
5. Upon selection:
   - The verified Google JWT token is processed and sent to the backend `/api/auth/google`.
   - Your account is automatically provisioned and logged in.
   - The header displays your profile picture or Google 'G' badge, and tickets are automatically linked to your personal account.

---

## Architecture of Google Auth in this System

```
+----------------------------+
| Google Identity Services   | (or Local Account Chooser)
+--------------+-------------+
               | JWT Token / Verified User Info
               v
+----------------------------+
| POST /api/auth/google      | (EmbeddedWebServer.java)
+--------------+-------------+
               |
               v
+----------------------------+
| AuthService.java           | 1. Matches existing user or auto-provisions new User
|                            | 2. Sets authProvider = "google"
|                            | 3. Generates 36-char secure Session Token (TOKEN-<uuid>)
+--------------+-------------+
               | Returns session token & user JSON
               v
+----------------------------+
| web/index.html             | 1. Stores token in localStorage('railway_auth_token')
| Client Storage             | 2. Displays Google avatar in header
|                            | 3. Loads personalized "My Bookings" history
+----------------------------+
```
