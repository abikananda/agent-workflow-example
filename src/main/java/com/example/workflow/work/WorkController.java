package com.example.workflow.work;

import com.example.workflow.model.Contracts.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work")
public class WorkController {
    private final WorkService service;
    public WorkController(WorkService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<WorkResponse> submit(@Valid @RequestBody WorkRequest request) {
        String id = service.submit(request);
        service.process(id, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new WorkResponse(id, "RECEIVED", null, null));
    }
    @GetMapping("/{id}")
    public WorkResponse get(@PathVariable String id) { return service.get(id); }
    @ExceptionHandler(WorkService.WorkNotFoundException.class)
    public ResponseEntity<String> notFound(WorkService.WorkNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
