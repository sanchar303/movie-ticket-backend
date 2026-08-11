package com.movie;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class FirebaseService {
    private static final String DB_URL = "https://movie-book-java-default-rtdb.asia-southeast1.firebasedatabase.app/movie-ticket-java";
    private final HttpClient client = HttpClient.newHttpClient();

    public String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(new URI(DB_URL + path + ".json")).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    public void put(String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(new URI(DB_URL + path + ".json"))
                .PUT(HttpRequest.BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
        client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public void post(String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(new URI(DB_URL + path + ".json"))
                .POST(HttpRequest.BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
        client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}