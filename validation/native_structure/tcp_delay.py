"""Opt-in loopback TCP delay for the native structure two-client smoke test.

Each received chunk retains stream order and is released after a fixed delay.
The read loop keeps running during that delay: latency does not accumulate once
per chunk. This simulates latency, not packet loss, jitter, or application reorder.
"""

import argparse
import asyncio
import contextlib
import json
import time


async def pipe(reader, writer, delay, label):
    queue = asyncio.Queue(maxsize=1024)
    counts = {"chunks": 0, "bytes": 0}

    async def receive():
        try:
            while data := await reader.read(65536):
                await queue.put((asyncio.get_running_loop().time() + delay, data))
        finally:
            if not asyncio.current_task().cancelling():
                await queue.put((0, None))

    receiver = asyncio.create_task(receive())
    try:
        while True:
            due, data = await queue.get()
            if data is None:
                break
            await asyncio.sleep(max(0, due - asyncio.get_running_loop().time()))
            writer.write(data)
            await writer.drain()
            counts["chunks"] += 1
            counts["bytes"] += len(data)
    finally:
        receiver.cancel()
        with contextlib.suppress(asyncio.CancelledError, ConnectionError):
            await receiver
        writer.close()
        with contextlib.suppress(ConnectionError):
            await writer.wait_closed()
        print(json.dumps({"stream": label, **counts}), flush=True)


async def main(args):
    serial = 0

    async def connected(reader, writer):
        nonlocal serial
        serial += 1
        identity = serial
        print(json.dumps({"connected": identity, "time": time.time()}), flush=True)
        try:
            upstream_reader, upstream_writer = await asyncio.open_connection("127.0.0.1", args.target_port)
            await asyncio.gather(
                pipe(reader, upstream_writer, args.delay_ms / 1000, f"{identity}:client-to-server"),
                pipe(upstream_reader, writer, args.delay_ms / 1000, f"{identity}:server-to-client"),
            )
        except (ConnectionError, OSError) as error:
            print(json.dumps({"connection": identity, "error": str(error)}), flush=True)
        finally:
            writer.close()

    server = await asyncio.start_server(connected, "127.0.0.1", args.listen_port)
    print(json.dumps({"ready": True, "listen_port": args.listen_port, "target_port": args.target_port,
                      "delay_ms_each_direction": args.delay_ms, "reordering": False}), flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--listen-port", type=int, default=25576)
    parser.add_argument("--target-port", type=int, default=25575)
    parser.add_argument("--delay-ms", type=int, default=100)
    options = parser.parse_args()
    if not (1 <= options.listen_port <= 65535 and 1 <= options.target_port <= 65535
            and options.listen_port != options.target_port and 0 <= options.delay_ms <= 1000):
        parser.error("Use distinct valid ports and a delay in [0, 1000] ms")
    try:
        asyncio.run(main(options))
    except KeyboardInterrupt:
        pass
