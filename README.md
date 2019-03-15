# Functional Spring Boot Transaction
No magic! Run commands in translation explicitly

```java
transactionTemplate.execute(status -> {
  Message message = Message.of(msg);
  em.persist(message);
  return message;
});
```

links:

- [read more](https://docs.spring.io/spring/docs/current/spring-framework-reference/data-access.html#tx-prog-template-settings)
