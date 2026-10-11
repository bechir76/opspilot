package dev.opspilot.chaos;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP control plane for fault injection. Every mutating call returns the resulting status. */
@RestController
@RequestMapping("/chaos")
public class ChaosController {

    private final ChaosState state;

    public ChaosController(ChaosState state) {
        this.state = state;
    }

    @GetMapping
    public ChaosStatus status() {
        return state.status();
    }

    @PostMapping("/latency")
    public ChaosStatus latency(@RequestParam long ms) {
        state.setLatency(ms);
        return state.status();
    }

    @PostMapping("/errors")
    public ChaosStatus errors(@RequestParam double rate) {
        state.setErrorRate(rate);
        return state.status();
    }

    @PostMapping("/memory-leak")
    public ChaosStatus memoryLeak(@RequestParam int mbPerSecond) {
        state.startMemoryLeak(mbPerSecond);
        return state.status();
    }

    @PostMapping("/cpu")
    public ChaosStatus cpu(@RequestParam int seconds) {
        state.burnCpu(seconds);
        return state.status();
    }

    @PostMapping("/crash")
    public ResponseEntity<ProblemDetail> crash() {
        state.crash();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.ACCEPTED,
                "Process will exit in " + ChaosState.CRASH_DELAY_MS + " ms");
        body.setTitle("Crash scheduled");
        return ResponseEntity.accepted().body(body);
    }

    @PostMapping("/reset")
    public ChaosStatus reset() {
        state.reset();
        return state.status();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badInput(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
