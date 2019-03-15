package com.github.daggerok;

import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.*;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Data
@Entity
@NoArgsConstructor(access = PROTECTED)
@RequiredArgsConstructor(staticName = "of")
class Message {

  @Id
  @GeneratedValue
  @Setter(PRIVATE)
  UUID id;

  @NonNull
  @Setter(PRIVATE)
  @Basic(optional = false)
  @Column(nullable = false)
  String message;
}

@Log4j2
@SpringBootApplication
public class FunctionalSpringBootTransactionApplication {

  public static void main(String[] args) {
    SpringApplication.run(FunctionalSpringBootTransactionApplication.class, args);
  }

  @Bean
  TaskExecutor taskExecutor() {
    return new SimpleAsyncTaskExecutor("my-futures");
  }

  @RestController
  @RequiredArgsConstructor
  public static class RestResource {

    @PersistenceContext
    final EntityManager em;
    final TaskExecutor taskExecutor;
    final TransactionTemplate txTemplate;

    @PostMapping
    public CompletableFuture<Message> post(@RequestBody Map<String, String> request) {
      log.info("1) out of tx and future: request = {}", () -> request);
      return CompletableFuture.supplyAsync(() -> {
        log.info(() -> "3) out of tx, but in future thread");
        return txTemplate.execute(status -> {
          String msg = Objects.requireNonNull(request.get("msg"));
          Message message = Message.of(msg);
          em.persist(message);
          log.info("3) in tx in future: message = {}, status = {}", () -> message, () -> status);
          return message;
        });
      }, taskExecutor);
    }

    @RequestMapping
    CompletableFuture<Iterable<Message>> get() {
      return CompletableFuture.supplyAsync(
          () -> txTemplate.execute(
              status -> {
                try (Stream<Message> any = em.createQuery("select m from Message m", Message.class).getResultStream()) {
                  return any.peek(message -> log.info("received {} with status {}", () -> message, () -> status))
                            .collect(Collectors.toList());
                }
              }
          ),
          taskExecutor
      );
    }
  }
}
