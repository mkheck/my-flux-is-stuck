package com.thehecklers.coffeeservice;

import lombok.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@SpringBootApplication
public class CoffeeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoffeeServiceApplication.class, args);
    }
}

@Component
class DataLoader {
    private final CoffeeRepo repo;

    DataLoader(CoffeeRepo repo) {
        this.repo = repo;
    }

    @PostConstruct
    private void load() {
        repo.deleteAll().thenMany(
                Flux.just("Blue Bottle Coffee", "Philz Coffee", "Cafe Cereza")
                        .map(name -> new Coffee(UUID.randomUUID().toString(), name))
                        .flatMap(repo::save))
                .thenMany(repo.findAll())
                .subscribe(System.out::println);
    }
}

@RestController
@RequestMapping("/coffees")
class CoffeeController {
    private final CoffeeService service;

    CoffeeController(CoffeeService service) {
        this.service = service;
    }

    @GetMapping
    Flux<Coffee> all() {
        return service.getAllCoffees();
    }

    @GetMapping("/{id}")
    Mono<Coffee> byId(@PathVariable String id) {
        return service.getCoffeeById(id);
    }

    @GetMapping(value = "/{id}/orders", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<CoffeeOrder> orders(@PathVariable String id) {
        return service.getOrders(id);
    }
}

@Service
class CoffeeService {
    private final CoffeeRepo repo;

    CoffeeService(CoffeeRepo repo) {
        this.repo = repo;
    }

    Flux<Coffee> getAllCoffees() {
        return repo.findAll();
    }

    Mono<Coffee> getCoffeeById(String id) {
        return repo.findById(id);
    }

    Flux<CoffeeOrder> getOrders(String coffeeId) {
        return Flux.<CoffeeOrder>generate(sink -> sink.next(new CoffeeOrder(coffeeId, Instant.now())))
                .delayElements(Duration.ofSeconds(1))
                .log();
    }
}

interface CoffeeRepo extends ReactiveCrudRepository<Coffee, String> {
}

@Value
class CoffeeOrder {
    private String coffeeId;
    private Instant whenOrdered;
}

@Table
@Value
class Coffee {
    @Id
    private String id;
    private String name;
}