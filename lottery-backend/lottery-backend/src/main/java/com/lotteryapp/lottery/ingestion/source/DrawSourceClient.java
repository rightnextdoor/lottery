package com.lotteryapp.lottery.ingestion.source;

import java.util.Map;

public interface DrawSourceClient {

    FetchedContent fetch(String url, Map<String, String> headers);

    record FetchedContent(byte[] bytes, String contentType, String finalUrl, int statusCode) {}
}
