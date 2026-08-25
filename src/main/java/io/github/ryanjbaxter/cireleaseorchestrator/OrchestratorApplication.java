package io.github.ryanjbaxter.cireleaseorchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.ryanjbaxter.cireleaseorchestrator.cli.Usage;

@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        if (Usage.isRequested(args)) {
            Usage.print();
            return;
        }
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
