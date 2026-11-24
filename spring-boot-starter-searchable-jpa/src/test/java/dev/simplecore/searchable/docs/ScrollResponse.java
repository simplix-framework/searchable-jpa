package dev.simplecore.searchable.docs;

import java.util.List;

// docs:start response
public record ScrollResponse(List<PostResponse> items, String next) {
}
// docs:end response
