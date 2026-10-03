package co.ke.bremac.posapp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class ReceiptPrinterTransport {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int JOB_TIMEOUT_SECONDS = 25;

    private ReceiptPrinterTransport() {
    }

    static String validateHost(String input) {
        String host = input == null ? "" : input.trim();
        if (host.isEmpty() || host.length() > 253 || !host.matches("[A-Za-z0-9._:%-]+")) {
            throw new IllegalArgumentException("Enter a host name or IP address, without a URL or port.");
        }
        boolean ipv6 = host.indexOf(':') >= 0;
        if (ipv6) {
            if (host.indexOf(':') == host.lastIndexOf(':')
                    || !host.matches("[0-9A-Fa-f:.]+(?:%[A-Za-z0-9_-]+)?")) {
                throw new IllegalArgumentException("Enter an IPv6 address without brackets or a port.");
            }
        } else if (!host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")) {
            throw new IllegalArgumentException("Enter a valid printer host name or IP address.");
        }
        if (!ipv6) {
            for (String label : host.split("\\.", -1)) {
                if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                    throw new IllegalArgumentException("Enter a valid printer host name or IP address.");
                }
            }
            if (host.matches("[0-9.]+") && host.contains(".")) {
                String[] parts = host.split("\\.");
                if (parts.length != 4) throw new IllegalArgumentException("Enter a valid IPv4 address.");
                for (String part : parts) {
                    if (part.length() > 3 || Integer.parseInt(part) > 255) {
                        throw new IllegalArgumentException("Enter a valid IPv4 address.");
                    }
                }
            }
        }
        return host;
    }

    static int validatePort(String input) {
        try {
            int port = Integer.parseInt(input.trim());
            if (port >= 1 && port <= 65535) return port;
        } catch (RuntimeException ignored) {
        }
        throw new IllegalArgumentException("Printer port must be from 1 to 65535 (usually 9100).");
    }

    static void network(String host, int port, byte[] bytes) throws IOException {
        bounded((resource, cancelled) -> {
            Socket socket = new Socket();
            resource.set(socket);
            try (Socket ignored = socket) {
                checkCancelled(cancelled);
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(CONNECT_TIMEOUT_MS);
                send(socket.getOutputStream(), bytes, cancelled);
            }
        });
    }

    static void bluetooth(BluetoothDevice device, byte[] bytes) throws IOException {
        bounded((resource, cancelled) -> {
            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP);
            resource.set(socket);
            try (BluetoothSocket ignored = socket) {
                checkCancelled(cancelled);
                socket.connect();
                checkCancelled(cancelled);
                send(socket.getOutputStream(), bytes, cancelled);
            }
        });
    }

    private interface Operation {
        void run(AtomicReference<Closeable> resource, AtomicBoolean cancelled) throws Exception;
    }

    private static void bounded(Operation operation) throws IOException {
        AtomicReference<Closeable> resource = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        FutureTask<Void> work = new FutureTask<>(() -> {
            operation.run(resource, cancelled);
            return null;
        });
        Thread worker = new Thread(work, "receipt-printer-io");
        worker.setDaemon(true);
        worker.start();
        try {
            // Bounds DNS resolution, Bluetooth connect, and blocking writes, not just TCP connect.
            work.get(JOB_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            throw new IOException("Printer timed out after 25 seconds. Some bytes may have been sent; "
                    + "check the printer before retrying.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Printer operation interrupted. Check the printer before retrying.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            throw new IOException("Could not send receipt: "
                    + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage())
                    + ". Check connection, permissions, IP/port, and printer. A partial receipt is possible.",
                    cause);
        } finally {
            cancelled.set(true);
            work.cancel(true);
            Closeable socket = resource.get();
            // A vendor Bluetooth stack can ignore interruption; closing its socket unblocks I/O.
            if (socket != null && worker.isAlive()) closeLater(socket);
        }
    }

    private static void send(OutputStream out, byte[] bytes, AtomicBoolean cancelled) throws IOException {
        for (int offset = 0; offset < bytes.length; offset += 1024) {
            checkCancelled(cancelled);
            out.write(bytes, offset, Math.min(1024, bytes.length - offset));
        }
        checkCancelled(cancelled);
        out.flush();
    }

    private static void checkCancelled(AtomicBoolean cancelled) throws IOException {
        if (cancelled.get()) throw new IOException("Printer operation cancelled.");
    }

    private static void closeLater(Closeable socket) {
        Thread closer = new Thread(() -> {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }, "receipt-printer-close");
        closer.setDaemon(true);
        closer.start();
    }
}
