# lucenesim

You can use this if you are comparing Lucene merge policies without running full indexing benchmarks. I wrote it because I wanted to understand how TieredMergePolicy and LogByteSizeMergePolicy behave under different workloads, especially with deletes and real benchmarks take too long to iterate on.

## What it does

You describe a workload (how many flushes, how big, how many deletes) and the tool runs three merge policies against it:

- TieredMergePolicy (Lucene default)
- LogByteSizeMergePolicy
- TieredMergePolicy with a tuned segmentsPerTier

It prints WAF (write amplification factor), final segment count, number of merges, and peak segments. All in under a second.

## Quick start

```bash
./gradlew run                    # default: 100 flushes, 10K docs, no deletes
./gradlew run --args="--sweep"  # compare across 6 workload shapes
```

## Example output

```
Workload: 100 flushes × 10K docs, 1.0 MiB flush, 5% deletes

+--------------------------------------------+--------+----------+------------+------------+--------------+
| Policy                                     |    WAF | Segments |     Merges |  Peak Segs |  Flush Bytes |
+--------------------------------------------+--------+----------+------------+------------+--------------+
| TieredMergePolicy                          |   4.90 |        6 |         70 |         11 |    500.0 MiB |
| LogByteSizeMergePolicy                     |  12.54 |       14 |         74 |         25 |    500.0 MiB |
| TieredMergePolicy(segmentsPerTier=10.0)    |   5.21 |        9 |         54 |         13 |    500.0 MiB |
+--------------------------------------------+--------+----------+------------+------------+--------------+
```

## How it works (short version)

The trick: instead of building a real index, the tool creates fake SegmentCommitInfo objects with realistic sizes and feeds them to Lucene's actual MergePolicy.findMerges(). The merge policy runs its real code, picks which segments to merge, and the simulator executes those merges by creating new fake segments with prorated sizes.

No reimplementation of merge logic. If Lucene changes how TieredMergePolicy works, this tool automatically picks it up.

## What's missing

No I/O modeling, can't tell you how long indexing takes on your SSD

No actual documents, no analyzers, no queries

Deletes are distributed randomly across segments, not targeted by term like real IndexWriter

So: useful for comparing policies and tuning parameters, not for predicting absolute production performance.

## Using it from code

```java
WorkloadSource workload = SyntheticWorkloadBuilder.create()
    .flushCount(100)
    .flushDocCount(10_000)
    .deleteRate(0.10)
    .seed(42)
    .build();

SimulationResult result = new SimulationEngine(
    workload,
    new TieredMergePolicy(),
    new SerialSimScheduler(),
    new MetricsCollector(),
    false,  // debug
    42      // delete RNG seed
).run();

System.out.println("WAF: " + result.metrics().writeAmplificationFactor());
System.out.println("Segments: " + result.metrics().finalSegmentCount());
```

## Tests

```bash
./gradlew test
```

GoldTest - checks simulator output matches real IndexWriter within ±1 segment

WAFMathTest - verifies binary merge tree produces WAF = 3.0 (known result)

CalibrationTest - size estimator produces reasonable values

ConcurrentSchedulerTest - concurrent scheduler with infinite throughput matches serial scheduler

## Building

Requires Java 21. The vendored lucene-core-11.0.0-SNAPSHOT.jar in libs/ is the only dependency for the main code. JUnit 5 is pulled from Maven Central for tests.

## License

Apache 2.0. All source files have the standard ASF header.
