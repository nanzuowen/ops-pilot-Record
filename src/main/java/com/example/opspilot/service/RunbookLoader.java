package com.example.opspilot.service;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import org.springframework.ai.document.Document;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class RunbookLoader implements ApplicationRunner {
    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;

    public RunbookLoader(VectorStore vectorStore,ResourceLoader resourceLoader){
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception{
        List<Document> documents = List.of(
                loadDocument(
                        "classpath:runbooks/redis-pool-exhausted.md",
                        "redis-pool-exhausted"
                ),
                loadDocument(
                        "classpath:runbooks/mysql-slow-query.md",
                        "mysql-slow-query"
                ),
                loadDocument(
                        "classpath:runbooks/downstream-timeout.md",
                        "downstream-timeout"
                )
        );

        vectorStore.add(documents);

        System.out.println(
                "[RAG] Loaded" + documents.size()
                + "runbooks into VectorStore"
        );
    }

    private Document loadDocument(String path, String caseName) throws IOException{
        Resource resource = resourceLoader.getResource(path);

        String content =
                resource.getContentAsString(StandardCharsets.UTF_8);

        return Document.builder()
                .text(content)
                .metadata("case", caseName)
                .metadata("source", path)
                .build();
    }
}
