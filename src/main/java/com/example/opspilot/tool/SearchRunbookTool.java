package com.example.opspilot.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.ai.document.Document;

import java.util.List;

@Slf4j
@Component
public class SearchRunbookTool {

    private final VectorStore vectorStore;

    public SearchRunbookTool(VectorStore vectorStore){
        this.vectorStore = vectorStore;
    }

    @Tool(
            name = "searchRunbook",
            description = """
                    根据故障现象语义搜索运维Runbook。
                    当需要查询故障排查步骤，判断依据或处理建议时使用。
                    """
    )
    public String searchRunbook(
            @ToolParam(description = "需要排查的故障现象或问题描述")
            String query
    ){
        log.info("Tool searchRunbook called, query={}", query);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(1)
                .similarityThreshold(0.7)
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);

        if (documents.isEmpty()){
            return "没有找到相关 Runbook";
        }

        Document document = documents.getFirst();

        return """
            case: %s
            score: %s

            %s
            """.formatted(
                document.getMetadata().get("case"),
                document.getScore(),
                document.getText()
        );
    }

}
