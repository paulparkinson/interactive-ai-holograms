// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/

package oracleai.vectorrag.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class VectorRAGService {

    @Autowired
    private volatile VectorStore vectorStore;

    private static final Logger logger = LoggerFactory.getLogger(VectorRAGService.class);

    private final ChatClient aiClient;

    VectorRAGService(@Qualifier("openAiChatClient") ChatClient aiClient) {
        this.aiClient = aiClient;
    }

    @Value("classpath:prompt-template.txt")
    private Resource templateFile;
 
    private final String templateBasic = """
        DOCUMENTS:
        {documents}
        
        QUESTION:
        {question}
        
        INSTRUCTIONS:
        Answer the users question using the DOCUMENTS text above.
        Keep your answer ground in the facts of the DOCUMENTS.
        If the DOCUMENTS doesn't contain the facts to answer the QUESTION, return: 
        I'm sorry but I haven't enough information to answer.
        """;

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public void putDocument(Resource docResource) {
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(docResource,
                PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0)
                    .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                        .withNumberOfTopTextLinesToDelete(0)
                        .build())
                    .withPagesPerDocument(1)
                    .build());

        var textSplitter = new TokenTextSplitter();
        this.vectorStore.accept(textSplitter.apply(pdfReader.get()));
    }

    public String rag(String question) {
        String START = "\n<article>\n";
        String STOP = "\n</article>\n";
        
        List<Document> similarDocuments = this.vectorStore.similaritySearch( 
            SearchRequest.
               query(question).
               withTopK(4));

        Iterator<Document> iterator = similarDocuments.iterator();
        StringBuilder context = new StringBuilder();
        while (iterator.hasNext()) {
            Document document = iterator.next();
            context.append(document.getId() + ".");
            context.append(START + document.getFormattedContent() + STOP);
        }

        String template = templateBasic;
        try {
            template = new String(Files.readAllBytes(templateFile.getFile().toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error(e.getMessage());
            template = templateBasic;
        }

        PromptTemplate promptTemplate = new PromptTemplate(template);
        Prompt prompt = promptTemplate.create(Map.of("documents", context, "question", question));
        logger.info(prompt.toString());
        ChatResponse aiResponse = aiClient.call(prompt);
        return aiResponse.getResult().getOutput().getContent();
    }

    public List<Document> getSimilarDocs(String message) {
        List<Document> similarDocuments = this.vectorStore.similaritySearch(message);
        return similarDocuments;
    }
}
