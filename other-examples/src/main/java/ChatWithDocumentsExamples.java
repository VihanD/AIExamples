import dev.langchain4j.chain.ConversationalRetrievalChain;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.ParagraphSplitter;
import dev.langchain4j.data.document.splitter.SentenceSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.document.DocumentType.TXT;
import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_EMBEDDING_ADA_002;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;

public class ChatWithDocumentsExamples {

    // Please also check ServiceWithRetrieverExample

    static class IfYouNeedSimplicity {

        public static void main(String[] args) throws Exception {

            Document document = loadDocument(toPath("example-files/story-about-happy-carrot.txt"));

            EmbeddingModel embeddingModel = OpenAiEmbeddingModel.withApiKey(ApiKeys.OPENAI_API_KEY);

            EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .splitter(new ParagraphSplitter())
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(document);

            ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                    .chatLanguageModel(OpenAiChatModel.withApiKey(ApiKeys.OPENAI_API_KEY))
                    .retriever(EmbeddingStoreRetriever.from(embeddingStore, embeddingModel))
                    // .chatMemory() // you can override default chat memory
                    // .promptTemplate() // you can override default prompt template
                    .build();

            String answer = chain.execute("Who is Charlie?");
            System.out.println(answer); // Charlie is a cheerful carrot living in VeggieVille...
        }
    }

    static class If_You_Need_More_Control {

        public static void main(String[] args) {

            // Load the document that includes the information you'd like to "chat" about with the model.
            // Currently, loading text and PDF files from file system and by URL is supported.
            Document document = loadDocument(toPath("example-files/story-about-happy-carrot.txt"), TXT);

            // Split document into segments (one paragraph per segment)
            DocumentSplitter splitter = new SentenceSplitter();
            List<TextSegment> segments = splitter.split(document);

            // Embed segments (convert them into vectors that represent the meaning) using OpenAI embedding model
            // You can also use HuggingFaceEmbeddingModel (free)
            EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                    .apiKey(ApiKeys.OPENAI_API_KEY)
                    .modelName(TEXT_EMBEDDING_ADA_002)
                    .timeout(ofSeconds(15))
                    .logRequests(true)
                    .logResponses(true)
                    .build();

            List<Embedding> embeddings = embeddingModel.embedAll(segments);

            // Store embeddings into embedding store for further search / retrieval
            EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
            embeddingStore.addAll(embeddings, segments);

            // Specify the question you want to ask the model
            String question = "Who is Charlie?";

            // Embed the question
            Embedding questionEmbedding = embeddingModel.embed(question);

            // Find relevant embeddings in embedding store by semantic similarity
            // You can play with parameters below to find a sweet spot for your specific use case
            int maxResults = 3;
            double minScore = 0.8;
            List<EmbeddingMatch<TextSegment>> relevantEmbeddings
                    = embeddingStore.findRelevant(questionEmbedding, maxResults, minScore);

            // Create a prompt for the model that includes question and relevant embeddings
            PromptTemplate promptTemplate = PromptTemplate.from(
                    "Answer the following question to the best of your ability:\n"
                            + "\n"
                            + "Question:\n"
                            + "{{question}}\n"
                            + "\n"
                            + "Base your answer on the following information:\n"
                            + "{{information}}");

            String information = relevantEmbeddings.stream()
                    .map(match -> match.embedded().text())
                    .collect(joining("\n\n"));

            Map<String, Object> variables = new HashMap<>();
            variables.put("question", question);
            variables.put("information", information);

            Prompt prompt = promptTemplate.apply(variables);

            // Send the prompt to the OpenAI chat model
            ChatLanguageModel chatModel = OpenAiChatModel.builder()
                    .apiKey(ApiKeys.OPENAI_API_KEY)
                    .modelName(GPT_3_5_TURBO)
                    .temperature(0.7)
                    .timeout(ofSeconds(15))
                    .maxRetries(3)
                    .logResponses(true)
                    .logRequests(true)
                    .build();

            AiMessage aiMessage = chatModel.sendUserMessage(prompt.toUserMessage());

            // See an answer from the model
            String answer = aiMessage.text();
            System.out.println(answer); // Charlie is a cheerful carrot living in VeggieVille...
        }
    }

    static class Weaviate_Vector_Database_Example {

        public static void main(String[] args) throws Exception {
            Document document = loadDocument(toPath("example-files/story-about-happy-carrot.txt"));

            EmbeddingModel embeddingModel = OpenAiEmbeddingModel.withApiKey(
                    ApiKeys.OPENAI_API_KEY
            );

            EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore
                    .builder()
                    // find it under "Show API keys" of your Weaviate cluster.
                    .apiKey(System.getenv("WEAVIATE_API_KEY"))
                    // the scheme, e.g. "https" of cluster URL. Find in under Details of your Weaviate cluster.
                    .scheme("https")
                    // the host, e.g. "langchain4j-4jw7ufd9.weaviate.network" of cluster URL.
                    // Find it under Details of your Weaviate cluster.
                    .host("your_host")
                    // Default class is used if not specified
                    .objectClass("CharlieCarrot")
                    // if true (default), then WeaviateEmbeddingStore will generate a hashed ID based on provided
                    // text segment, which avoids duplicated entries in DB. If false, then random ID will be generated.
                    .avoidDups(true)
                    // Consistency level: ONE, QUORUM (default) or ALL.
                    .consistencyLevel("ALL")
                    .build();

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor
                    .builder()
                    .splitter(new ParagraphSplitter())
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(document);

            ConversationalRetrievalChain chain = ConversationalRetrievalChain
                    .builder()
                    .chatLanguageModel(OpenAiChatModel.withApiKey(ApiKeys.OPENAI_API_KEY))
                    .retriever(EmbeddingStoreRetriever.from(embeddingStore, embeddingModel))
                    .build();

            String answer = chain.execute("Who is Charlie? Answer in 10 words.");
            System.out.println(answer);
        }
    }

    private static Path toPath(String fileName) {
        try {
            URL fileUrl = ChatWithDocumentsExamples.class.getResource(fileName);
            return Paths.get(fileUrl.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
