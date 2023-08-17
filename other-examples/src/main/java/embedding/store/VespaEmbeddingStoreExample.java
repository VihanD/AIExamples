package embedding.store;

import static dev.langchain4j.model.inprocess.InProcessEmbeddingModelType.ALL_MINILM_L6_V2;
import static java.util.Arrays.asList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.inprocess.InProcessEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.VespaEmbeddingStoreImpl;
import java.util.List;

public class VespaEmbeddingStoreExample {

  public static void main(String[] args) {
    // Requires "langchain4j-vespa" Maven/Gradle dependency

    EmbeddingStore<TextSegment> embeddingStore = VespaEmbeddingStoreImpl
      .builder()
      .url(
        "https://alexey-heezer.carrot.mytenant346.aws-us-east-1c.dev.z.vespa-app.cloud"
      )
      .keyPath(
        "/Users/alexey.titov/.vespa/mytenant346.carrot.alexey-heezer/data-plane-private-key.pem"
      )
      .certPath(
        "/Users/alexey.titov/.vespa/mytenant346.carrot.alexey-heezer/data-plane-public-cert.pem"
      )
      .build();

    InProcessEmbeddingModel embeddingModel = new InProcessEmbeddingModel(
      ALL_MINILM_L6_V2
    );

    TextSegment segment1 = TextSegment.from("I like football.");
    Embedding embedding1 = embeddingModel.embed(segment1);
    embeddingStore.add(embedding1, segment1);

    TextSegment segment2 = TextSegment.from("I've never been to New York.");
    Embedding embedding2 = embeddingModel.embed(segment2);
    embeddingStore.add(embedding2, segment2);
    //
    TextSegment segment3 = TextSegment.from(
      "But actually we tried our new swimming pool yesterday and it was awesome!"
    );
    Embedding embedding3 = embeddingModel.embed(segment3);
    embeddingStore.add(embedding3, segment3);

    List<String> ids = embeddingStore.addAll(
      asList(embedding1, embedding2, embedding3),
      asList(segment1, segment2, segment3)
    );

    Embedding queryEmbedding = embeddingModel.embed(
      "What is your favorite sport?"
    );
    List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(
      queryEmbedding,
      2
    );

    System.out.println(relevant.get(0).score()); // 0.734...
    System.out.println(relevant.get(0).embedded().text()); // football
    System.out.println(relevant.get(1).score()); // 0.567...
    System.out.println(relevant.get(1).embedded().text()); // swimming pool
  }
}
