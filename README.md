# Ranobe_web
A web application publishing and read novel, light novel, manga(maybe)

## Features

### Readers
- Browse product and show their details
- Read chapters with text and illustration
- View publisher's profile and works

### Publishers
- Create and edit their own products
- Assign multiple genres to a story
- Manage volumes, chapters, cover images, and summaries
- Write chapters with text formatting and embedded images
- Import content from text-based PDF documents, preview it, and make edits before saving
- Public or private product (except admin/mod)

### Administrations

- Administrations and moderators can hide and restore product
- Ban and unban user

## Technology Stack
- Java and Spring Boot: development and execution
- PostgreSQL: data storage
- Spring Data JPA: database queries and mapping data to Java classes
- Cloudflare: image storage
- Apache PDFBox: read and analyze PDF documents

## Deployment

The application is deployed on Render using Docker
- **Render:** hosts the Spring Boot application and Thymeleaf pages
- **Neon:** hosts the PostgreSQL database
- **Cloudflare R2:** stores images

The Render is free so you maybe have to wait a few minutes:) enjoy

Link 👉 https://ranobe-web.onrender.com/