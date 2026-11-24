# Pending & Deferred Tests

The user requested prioritizing end-to-end features for device testing. The following test suites are tracked here for later expansion:

## Deferred Tests
- `LlmEngineIntegrationTest`: End-to-end generation with quantized GGUF weights on device.
- `OfflineEvalHarness`: Full benchmark running `testdata/eval.jsonl` against recorded screen dumps.
- `SafetyGuardExhaustiveTest`: Large dictionary fuzzing of restricted keywords and payment apps.
- `ScreenGraphStressTest`: Deep graph traversal and Dijkstra route finding with 100+ screens.
- `TeachModeReplayTest`: Full replay of accessibility gesture logs.
