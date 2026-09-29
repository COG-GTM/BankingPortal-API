# Banking Portal Rest API Using Spring Boot & Spring Security

## Fork and Star ⭐ Github Repo For New Feature Update

[![GitHub Repo](https://img.shields.io/badge/GitHub-UI%20Repo-blue.svg?style=flat-square)](https://github.com/abhi9720/BankingPortal-UI)
[![GitHub Repo](https://img.shields.io/badge/GitHub-API%20Repo-blue.svg?style=flat-square)](https://github.com/abhi9720/BankingPortal-API)

## API Documentation

- [https://github.com/abhi9720/BankingPortal-API/wiki](https://github.com/abhi9720/BankingPortal-API/wiki)

## Banking Portal UI

- [https://github.com/abhi9720/BankingPortal-UI](https://github.com/abhi9720/BankingPortal-UI)

![image](https://github.com/abhi9720/BankingPortal-API/assets/68281476/237694d9-6e8d-48e8-a7a2-982b9f8ca671)

***

The Banking Portal API provides a set of endpoints for managing user accounts, fund transfers, and transactions. This project aims to facilitate secure and efficient banking operations for users.

## Features

- User Registration: Users can register by providing their details, such as name, email, address, and phone number.
- PIN Management: Users can create and update their PINs for added security.
- Cash Deposit and Withdrawal: Users can deposit and withdraw cash from their accounts.
- Fund Transfer: Users can transfer funds to other accounts within the system.
- Transaction History: Users can view their transaction history.

## Technologies Used

![image](https://github.com/abhi9720/BankingPortal-API/assets/68281476/31896d20-16d9-4fe1-a534-0490841de4b9)
![image](https://github.com/abhi9720/BankingPortal-API/assets/68281476/c09bc4ac-c0ca-4f7c-9c6e-8eb9818eb35b)
![image](https://github.com/abhi9720/BankingPortal-API/assets/68281476/78c75fff-e8a8-49c6-9897-34b08b2c9308)
![image](https://github.com/abhi9720/BankingPortal-API/assets/68281476/3647613e-1d6e-4bc4-98b6-2da5648659f9)
![image](https://github.com/abhi9720/BankingPortal-API/assets/68281476/8a5c0b00-776b-444e-bc24-36fc6bfe4c41)
![image](https://github.com/abhi9720/BankingPortal-API/assets/68281476/b56a7167-6a3a-49a0-8b8a-8a4e3e71a383)
![image](https://github.com/abhi9720/BankingPortal-API/assets/68281476/b5c86e65-cbe8-400a-afeb-895846601da7)

## TODO

- UI Fix for Dashboard Charts
- Pagination in table
- Save JWT Token in db and remove on logout
- Email trigger on account login
- Send Bank Statement on Email

## Installation and Setup

1. Clone the repository: `git clone https://github.com/yourusername/banking-portal-api.git`
2. Navigate to the project folder: `cd banking-portal-api`
3. Configure MySQL: Set up a MySQL database, create a copy of `application.properties.sample`, rename it `application.properties`, and update the properties as needed.
4. Build and run the project: `mvn spring-boot:run`

## Account lockout and login backoff

Password logins (`POST /api/users/login`) are protected by a per-account lockout with exponential backoff.

**Behaviour**

- Failed password attempts are counted per account number (identifier resolved to the account first).
- When the consecutive failure count reaches `max-attempts`, the account is locked for `base-lock-duration`.
- While locked, every password login for that account is rejected with `429 Too Many Requests`, a `Retry-After` header (seconds) and a message, **without checking the password**. Attempts during a lock do not extend it.
- After the lock expires the user gets one attempt. Each further failure re-locks for `base-lock-duration * backoff-multiplier^(failures - max-attempts)`, capped at `max-lock-duration`. With defaults: 1m, 2m, 4m, 8m, ... 1h.
- A successful login or a successful password reset clears the counter and any lock.
- If no failure happens for `failure-reset-window`, the next failure starts counting from 1 again.
- OTP login (`/api/users/verify-otp`) and the password-reset flow are not blocked by the lockout, so a locked-out user can still recover through their email.

**Configuration** (`application.properties`, env vars such as `SECURITY_LOGINLOCKOUT_MAXATTEMPTS` also work)

| Property | Default | Description |
| --- | --- | --- |
| `security.login-lockout.enabled` | `true` | Kill switch. When `false`, failures are not recorded and no account is locked. |
| `security.login-lockout.max-attempts` | `5` | Consecutive failures before the first lock (>= 1). |
| `security.login-lockout.base-lock-duration` | `1m` | Duration of the first lock (> 0). |
| `security.login-lockout.backoff-multiplier` | `2.0` | Growth factor per additional failure (>= 1.0; `1.0` = fixed-duration lock). |
| `security.login-lockout.max-lock-duration` | `1h` | Cap on a single lock (>= `base-lock-duration`). |
| `security.login-lockout.failure-reset-window` | `24h` | Idle period after which the failure counter is forgotten (> 0). |

Durations accept Spring Boot formats (`30s`, `15m`, `PT1H`). Invalid values fail application startup.

**Operations**

- State lives in the `login_attempt` table (`account_number`, `failed_attempts`, `last_failure_at`, `locked_until`, UTC), so it is shared across instances and survives restarts. The table is created by Hibernate `ddl-auto`.
- Manually unlock an account: `DELETE FROM login_attempt WHERE account_number = '<account>';`
- Locks are logged at `WARN` by `LoginAttemptServiceImpl` (`Account <n> locked for <s>s after <k> consecutive failed logins`); alert on a spike of these to detect credential-stuffing.
- Lock expiry uses the application server clock; keep instance clocks NTP-synced.
- The lockout is per account, not per IP. An attacker who knows an account number can keep it locked (bounded by `max-lock-duration` per attempt); pair it with IP-based rate limiting at the edge.

## Screenshots

![project](https://github.com/abhi9720/BankingPortal-API/assets/68281476/45bca1e0-0af2-4d63-a8d0-efd7b67df6bf)

## Error Handling

The API implements global exception handling for common error scenarios, such as account not found, unauthorized access, and insufficient balance.

## How to Contribute

We welcome and encourage developers to contribute to the project and help us make it even better. If you are interested in contributing, follow these steps:

👉🏻**Fork the Repository**: Click on the "Fork" button on the top right corner of the GitHub repository page. This will create a copy of the repository in your GitHub account.

👉🏻**Clone the Forked Repository**: Open your terminal or command prompt and use the following command to clone the repository to your local machine:

   ```sh
   git clone https://github.com/your-username/BankingPortal-API.git
   ```

   Replace `your-username` with your GitHub username.

👉🏻**Create a New Branch**: Move into the project directory using `cd BankingPortal-API` and create a new branch for your changes:

   ```sh
   git checkout -b feature/your-new-feature
   ```

   Replace `your-new-feature` with a descriptive name for your contribution.

👉🏻**Make Changes**: Now, make the desired changes to the codebase using your favorite code editor.

👉🏻**Commit Changes**: After making the changes, save your work and commit the changes with a meaningful commit message:

   ```sh
   git add .
   git commit -m "Add your commit message here"
   ```

👉🏻**Push Changes**: Push your changes to your forked repository:

   ```sh
   git push origin feature/your-new-feature
   ```

👉🏻**Create a Pull Request**: Go to your forked repository on GitHub, and you'll see a "Compare & Pull Request" button. Click on it to create a new pull request.

👉🏻**Wait for Review**: Your pull request will be reviewed by the project maintainers. Make any necessary changes based on their feedback.

**👏🏻👏🏻 Congratulations! 🎉🎊** Your contribution has been accepted and merged into the main repository. You are now a contributor to the project.
