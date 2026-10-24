"""Local-only TCP shaping for the opt-in VFX scene (Python standard library)."""
import argparse
import asyncio
import json
import random
import time
from collections import defaultdict
from pathlib import Path


async def main(args):
    done = asyncio.Event()
    connected = 0
    closed = 0
    results = []
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    async def connection(client_reader, client_writer):
        nonlocal connected, closed
        connected += 1
        number = connected
        started = time.monotonic()
        counts = defaultdict(int)
        phase_bytes = defaultdict(int)
        error = None
        server_writer = None
        tasks = []
        rng = random.Random(number)

        async def transfer(reader, writer, direction, rate):
            queue = asyncio.Queue(maxsize=64)

            async def receive():
                while data := await reader.read(4096):
                    delay = max(0, args.delay_ms + rng.uniform(-args.jitter_ms, args.jitter_ms)) / 1000
                    await queue.put((time.monotonic() + delay, data))
                await queue.put(None)

            async def send():
                deadline = time.monotonic()
                while item := await queue.get():
                    due, data = item
                    deadline = max(deadline, due) + len(data) / rate
                    await asyncio.sleep(max(0, deadline - time.monotonic()))
                    writer.write(data)
                    await writer.drain()
                    counts[direction] += len(data)
                    phase_file = output / "phase.txt"
                    phase = phase_file.read_text().strip() if phase_file.exists() else "loading"
                    phase_bytes[f"{direction}:{phase}"] += len(data)

            producer = asyncio.create_task(receive())
            try:
                await send()
                await producer
            finally:
                producer.cancel()
                await asyncio.gather(producer, return_exceptions=True)

        try:
            server_reader, server_writer = await asyncio.open_connection("127.0.0.1", 25575)
            tasks = [asyncio.create_task(transfer(server_reader, client_writer, "download", args.kib * 1024)),
                     asyncio.create_task(transfer(client_reader, server_writer, "upload", 256 * 1024))]
            completed, _ = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            for task in completed:
                task.result()
        except (ConnectionError, OSError) as failure:
            error = str(failure)
        finally:
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            for writer in (client_writer, server_writer):
                if writer is not None:
                    writer.close()
                    try:
                        await writer.wait_closed()
                    except (ConnectionError, OSError):
                        pass
            results.append(dict(connection=number, seconds=time.monotonic() - started,
                                bytes=dict(counts), phase_bytes=dict(phase_bytes), error=error))
            closed += 1
            if closed >= 2:
                done.set()

    server = await asyncio.start_server(connection, "127.0.0.1", 25576)
    print(f"127.0.0.1:25576 -> :25575; down={args.kib} KiB/s/peer, each-way delay={args.delay_ms} +/- {args.jitter_ms} ms", flush=True)
    async with server:
        await asyncio.wait_for(done.wait(), timeout=args.timeout)
    (output / "proxy-result.json").write_text(json.dumps(dict(
        download_kib=args.kib, each_way_delay_ms=args.delay_ms, jitter_ms=args.jitter_ms,
        connections=results), indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--kib", type=float, default=64)
    parser.add_argument("--delay-ms", type=float, default=100)
    parser.add_argument("--jitter-ms", type=float, default=30)
    parser.add_argument("--timeout", type=float, default=600)
    options = parser.parse_args()
    if options.kib <= 0 or options.timeout <= 0 or options.jitter_ms < 0:
        parser.error("Bandwidth and timeout must be positive; jitter cannot be negative")
    asyncio.run(main(options))
