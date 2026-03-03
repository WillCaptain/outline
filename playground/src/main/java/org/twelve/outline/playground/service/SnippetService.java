package org.twelve.outline.playground.service;

import org.twelve.outline.playground.db.SnippetRepository;
import org.twelve.outline.playground.model.Snippet;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SnippetService {

    private final SnippetRepository repo;

    public SnippetService(SnippetRepository repo) { this.repo = repo; }

    public List<Snippet> getAll(String userId)                            { return repo.findByUserId(userId); }
    public Snippet       save(String userId, String name, String code)    { return repo.insert(userId, name, code); }
    public Optional<Snippet> update(String userId, String id, String name, String code) {
        return repo.update(userId, id, name, code);
    }
    public boolean       delete(String userId, String snippetId)          { return repo.delete(userId, snippetId); }
}
