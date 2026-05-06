# 🤖 BudgetBot AI

**BudgetBot AI** is a production-ready AI-powered expense tracking SaaS application that allows users to manage their finances directly through **WhatsApp** using natural language.

Users can simply send messages like:

> “Spent 500 on food today”

and the system automatically:

* Receives the message via Twilio WhatsApp Sandbox
* Processes the message using Spring Boot webhook APIs
* Uses Spring AI + LLMs to extract structured expense data
* Stores expenses securely in PostgreSQL
* Displays analytics in a modern dashboard

---

# 🌐 Live Demo

🔗 **Deployed Application:**
https://focused-encouragement-production-b0cd.up.railway.app/

---

# ✨ Features

## 📲 WhatsApp Expense Tracking

Track expenses directly from WhatsApp using natural language.

### Example Messages

* Spent 500 on food today
* Paid 1200 for internet bill
* Bought groceries worth 850
* Spent 300 on petrol

The AI automatically extracts:

* Amount
* Category
* Note
* Date

---

# 🧠 AI-Powered Processing

Integrated with **Spring AI + OpenAI Compatible Models** to:

* Detect categories automatically
* Understand natural language
* Convert text into structured JSON
* Handle fallback parsing if AI fails

### AI Output Example

```json
{
  "amount": 500,
  "category": "Food",
  "note": "Spent on lunch",
  "date": "2026-04-25"
}
```

---

# 🏗️ Tech Stack

## Backend

* Java 17
* Spring Boot 3
* Spring Web
* Spring Data JPA (Hibernate)
* PostgreSQL
* Spring Security (JWT Authentication)
* Spring AI
* Twilio WhatsApp API
* Maven

## Frontend

* HTML5
* CSS3
* JavaScript
* Bootstrap 5
* Chart.js

## Deployment

* Railway
* PostgreSQL
* Ngrok (for local webhook testing)

---

# 🔐 Authentication Features

* User Registration
* JWT Login Authentication
* Secure Password Encryption
* Protected APIs
* User-based Expense Mapping

---

# 📊 Dashboard Analytics

Beautiful responsive dashboard with:

* Monthly Spending
* Weekly Spending
* Remaining Budget
* Expense Trends
* Category-wise Distribution
* Recent Transactions
* AI Insights

---

# 📂 Project Structure

```bash
budgetbot-ai/
│
├── src/main/java/com/budgetbot
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   ├── dto
│   ├── security
│   ├── ai
│   └── config
│
├── src/main/resources
│   ├── templates
│   ├── static
│   └── application.properties
│
├── frontend/
├── pom.xml
└── README.md
```

---

# 🗄️ Database Design

## User

| Field       | Type   |
| ----------- | ------ |
| id          | Long   |
| name        | String |
| email       | String |
| phoneNumber | String |
| password    | String |

## Expense

| Field    | Type           |
| -------- | -------------- |
| id       | Long           |
| amount   | Double         |
| category | String         |
| note     | String         |
| date     | LocalDate      |
| source   | WHATSAPP / WEB |
| user_id  | FK             |

## ChatHistory

| Field     | Type          |
| --------- | ------------- |
| id        | Long          |
| user_id   | FK            |
| message   | String        |
| response  | String        |
| timestamp | LocalDateTime |

---

# 🔌 REST APIs

## Authentication APIs

### Register

```http
POST /auth/register
```

### Login

```http
POST /auth/login
```

---

## Expense APIs

### Add Expense

```http
POST /expenses
```

### Get Expenses

```http
GET /expenses
```

### Expense Summary

```http
GET /expenses/summary
```

---

## WhatsApp Webhook

### Webhook Endpoint

```http
POST /webhook/whatsapp
```

Processes:

* Incoming WhatsApp messages
* AI parsing
* Expense creation
* Chat history storage

---

# ⚙️ Environment Variables

Create an `application.properties` file:

```properties
# Twilio
twilio.account.sid=${TWILIO_ACCOUNT_SID}
twilio.auth.token=${TWILIO_AUTH_TOKEN}
twilio.whatsapp.number=${TWILIO_WHATSAPP_NUMBER}

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/budgetbot
spring.datasource.username=postgres
spring.datasource.password=postgres

# Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# JWT
jwt.secret=your_secret_key
jwt.expiration=31536000000

# AI
spring.ai.openai.api-key=${OPENAI_API_KEY}
```

---

# 📲 Twilio WhatsApp Setup

## Step 1 — Join Sandbox

Send:

```bash
join <sandbox-code>
```

to:

```bash
+1 415 523 8886
```

---

## Step 2 — Configure Webhook

Set webhook URL in Twilio:

```bash
https://focused-encouragement-production-b0cd.up.railway.app/webhook/whatsapp
```

Method:

```bash
POST
```

---

# 🚀 Run Locally

## Clone Repository

```bash
git clone https://github.com/your-username/budgetbot-ai.git
```

## Navigate

```bash
cd budgetbot-ai
```

## Start PostgreSQL

Make sure PostgreSQL is running.

---

## Run Spring Boot

```bash
mvn spring-boot:run
```

---

# 📈 AI Chat Assistant

Users can ask:

* How much did I spend this week?
* Show my food expenses
* Which category has highest spending?
* How much money is left in my budget?

---

# 🛡️ Security Features

* JWT Authentication
* BCrypt Password Encryption
* User-specific Data Isolation
* Webhook Validation
* Exception Handling
* Input Validation

---

# 💡 Production-Ready Features

✅ Modular Architecture
✅ Clean Code Structure
✅ RESTful APIs
✅ AI Integration
✅ Railway Deployment
✅ PostgreSQL Persistence
✅ Responsive Dashboard
✅ WhatsApp Automation
✅ SaaS Ready
✅ Error Handling & Validation

---

# 🎯 Future Enhancements

* OCR Bill Scanner
* Voice Expense Tracking
* Multi-Currency Support
* Export Reports (PDF/Excel)
* Recurring Expense Detection
* Smart Budget Recommendations
* Mobile App Version
* Team/Family Expense Sharing

---

# 📸 Screenshots

## Dashboard

* Monthly spending analytics
* Pie charts
* Expense trends
* Recent transactions

## WhatsApp Integration

* AI-powered expense parsing
* Real-time tracking

---

# 👨‍💻 Developer

**Akash Sutradhar**
B.Tech CSE (CSBS) | Java Backend Developer
Passionate about building scalable AI-powered SaaS applications using Spring Boot, AI, and System Design.

---

# ⭐ If You Like This Project

Give this repository a ⭐ on GitHub and support the project!

---

# 📄 License

This project is licensed under the MIT License.

---

# 🔥 BudgetBot AI

> “Track your expenses with the power of AI + WhatsApp.”
