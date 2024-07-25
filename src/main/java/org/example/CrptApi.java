package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int requestLimit;
    private final long timeIntervalMillis;
    private final AtomicInteger requestCount;
    private final ReentrantLock lock;
    private final ScheduledExecutorService scheduler;
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.requestCount = new AtomicInteger(0);
        this.lock = new ReentrantLock(true);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduler.scheduleAtFixedRate(() -> requestCount.set(0), 0, this.timeIntervalMillis, TimeUnit.MILLISECONDS);
    }
    public void createDocument(Document document) throws Exception {
        lock.lock();
        try {
            while (requestCount.get() >= requestLimit) {
                synchronized (lock) {
                    lock.wait();
                }
            }
            requestCount.incrementAndGet();
        } finally {
            lock.unlock();
        }
        String jsonBody = objectMapper.writeValueAsString(document);
        System.out.println(jsonBody);
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("https://ismp.crpt.ru/api/v3/lk/documents/create")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to create document: " + response.body());
        }
        lock.lock();
        try {
            synchronized (lock) {
                lock.notifyAll();
            }
        } finally {
            lock.unlock();
        }
    }
    public static class Document {
        public String description, doc_id, doc_status, doc_type, owner_inn, participant_inn, producer_inn, production_date, production_type, reg_date, reg_number;
        public boolean importRequest;
        public Product[] products;
        public static class Product {
            public String certificate_document, certificate_document_date, certificate_document_number, owner_inn, producer_inn, production_date, tnved_code, uit_code, uitu_code;
        }
    }
    public static void main(String[] args) throws InterruptedException {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        Document.Product product = new Document.Product();
        product.certificate_document = "CertDoc";
        product.certificate_document_date = "2020-01-23";
        product.certificate_document_number = "CertDocNum";
        product.owner_inn = "OwnerINN";
        product.producer_inn = "ProducerINN";
        product.production_date = "2020-01-23";
        product.tnved_code = "TNVEDCode";
        product.uit_code = "UITCode";
        product.uitu_code = "UITUCode";

        Document document = new Document();
        document.description = "Description";
        document.doc_id = "DocID";
        document.doc_status = "DocStatus";
        document.doc_type = "LP_INTRODUCE_GOODS";
        document.importRequest = true;
        document.owner_inn = "OwnerINN";
        document.participant_inn = "ParticipantINN";
        document.producer_inn = "ProducerINN";
        document.production_date = "2020-01-23";
        document.production_type = "ProductionType";
        document.products = new Document.Product[]{product};
        document.reg_date = "2020-01-23";
        document.reg_number = "RegNumber";

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                try {
                    api.createDocument(document);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }
}