package org.geysermc.geyser.network.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;

import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class NetEaseCompressionCodec extends CompressionCodec {
    private static final int MAX_DECOMPRESSION_SIZE = 3 * 1024 * 1024;
    private static final int DEBUG_PREFIX_BYTES = 16;

    public NetEaseCompressionCodec(CompressionStrategy strategy, boolean prefixed) {
        super(strategy, prefixed);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) throws Exception {
        ByteBuf uncompressed = msg.getUncompressed();
        if (uncompressed == null) {
            throw new IllegalStateException("Batch was not encoded before");
        }

        if (uncompressed.readableBytes() == 0) {
            return;
        }

        try {
            byte secondByte = uncompressed.readableBytes() > 1 ? uncompressed.getByte(1) : 0;
            int secondValue = secondByte & 0xFF;
            if (secondValue == 0x8F) {
                ByteBuf finalData = ctx.alloc().ioBuffer(uncompressed.readableBytes() + 1);
                finalData.writeByte(0xFE);
                finalData.writeBytes(uncompressed, uncompressed.readerIndex(), uncompressed.readableBytes());
                out.add(finalData.retain());
                finalData.release();
            } else {
                ByteBuf compressedData = null;
                try {
                    compressedData = compressWithZlibRaw(ctx, uncompressed);
                    ByteBuf finalData = ctx.alloc().ioBuffer(compressedData.readableBytes() + 2);
                    finalData.writeByte(0xFE);
                    finalData.writeByte(0x00);
                    finalData.writeBytes(compressedData);
                    out.add(finalData.retain());
                    finalData.release();
                } catch (Exception e) {
                    ByteBuf finalData = ctx.alloc().ioBuffer(uncompressed.readableBytes() + 1);
                    finalData.writeByte(0xFE);
                    finalData.writeBytes(uncompressed, uncompressed.readerIndex(), uncompressed.readableBytes());
                    out.add(finalData.retain());
                    finalData.release();
                } finally {
                    if (compressedData != null) {
                        compressedData.release();
                    }
                }
            }
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) throws Exception {
        ByteBuf compressed = msg.getCompressed();
        if (compressed == null || !compressed.isReadable()) {
            return;
        }

        boolean neteasePrefixed = false;
        if (compressed.readableBytes() >= 1) {
            int firstByte = compressed.getByte(0) & 0xFF;
            if (firstByte == 0xFE) {
                compressed = compressed.slice(1, compressed.readableBytes() - 1);
                neteasePrefixed = true;
            } else if (firstByte == 0xFF) {
                compressed = compressed.slice(1, compressed.readableBytes() - 1);
            }
        }

        try {
            if (compressed.readableBytes() > MAX_DECOMPRESSION_SIZE) {
                throw new IllegalStateException("Compressed data too large: " + compressed.readableBytes());
            }

            ByteBuf dataToProcess;
            CompressionAlgorithm algorithm;
            ByteBuf decompressed;

            if (compressed.readableBytes() == 0) {
                decompressed = ctx.alloc().ioBuffer(0);
                algorithm = PacketCompressionAlgorithm.NONE;
            } else {
                int headerValue = compressed.getByte(0) & 0xFF;
                boolean hasStandardHeader = headerValue == 0x00 || headerValue == 0x01 || headerValue == 0xFF;

                if (headerValue == 0xFF && compressed.readableBytes() > 1) {
                    dataToProcess = compressed.slice(1, compressed.readableBytes() - 1);
                    decompressed = decompressWithNone(ctx, dataToProcess);
                    algorithm = PacketCompressionAlgorithm.NONE;
                } else if (headerValue == 0x00 && compressed.readableBytes() > 1) {
                    dataToProcess = compressed.slice(1, compressed.readableBytes() - 1);
                    try {
                        decompressed = decompressWithZlibRaw(ctx, dataToProcess);
                        algorithm = PacketCompressionAlgorithm.ZLIB;
                    } catch (Exception e) {
                        try {
                            decompressed = decompressWithZlib(ctx, dataToProcess);
                            algorithm = PacketCompressionAlgorithm.ZLIB;
                        } catch (Exception ex) {
                            decompressed = decompressWithNone(ctx, dataToProcess);
                            algorithm = PacketCompressionAlgorithm.NONE;
                        }
                    }
                } else if (hasStandardHeader) {
                    dataToProcess = compressed.slice(1, compressed.readableBytes() - 1);
                    decompressed = decompressWithNone(ctx, dataToProcess);
                    algorithm = PacketCompressionAlgorithm.NONE;
                } else {
                    dataToProcess = compressed.slice();
                    decompressed = decompressWithNone(ctx, dataToProcess);
                    algorithm = PacketCompressionAlgorithm.NONE;
                }
            }

            msg.setAlgorithm(algorithm);
            msg.setUncompressed(decompressed);
            this.onDecompressed(ctx, msg);
            out.add(msg.retain());
        } catch (Exception e) {
            throw e;
        }
    }

    private ByteBuf decompressWithNone(ChannelHandlerContext ctx, ByteBuf compressedData) {
        ByteBuf result = ctx.alloc().ioBuffer(compressedData.readableBytes());
        compressedData.markReaderIndex();
        result.writeBytes(compressedData);
        compressedData.resetReaderIndex();
        return result;
    }

    private ByteBuf decompressWithZlib(ChannelHandlerContext ctx, ByteBuf compressedData) throws DataFormatException {
        return decompressWithZlib(ctx, compressedData, false);
    }

    private ByteBuf decompressWithZlibRaw(ChannelHandlerContext ctx, ByteBuf compressedData) throws DataFormatException {
        return decompressWithZlib(ctx, compressedData, true);
    }

    private ByteBuf decompressWithZlib(ChannelHandlerContext ctx, ByteBuf compressedData, boolean raw) throws DataFormatException {
        Inflater inflater = new Inflater(raw);
        try {
            byte[] compressedBytes = new byte[compressedData.readableBytes()];
            compressedData.markReaderIndex();
            compressedData.readBytes(compressedBytes);
            compressedData.resetReaderIndex();
            inflater.setInput(compressedBytes);

            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];

            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput()) {
                        throw new DataFormatException("Zlib data incomplete");
                    }
                    break;
                }
                outputStream.write(buffer, 0, count);
                if (outputStream.size() > MAX_DECOMPRESSION_SIZE) {
                    throw new DataFormatException("Zlib output too large");
                }
            }

            byte[] decompressedBytes = outputStream.toByteArray();
            ByteBuf result = ctx.alloc().ioBuffer(decompressedBytes.length);
            result.writeBytes(decompressedBytes);
            return result;
        } finally {
            inflater.end();
        }
    }

    private ByteBuf compressWithZlibRaw(ChannelHandlerContext ctx, ByteBuf data) throws Exception {
        if (data.readableBytes() == 0) {
            return ctx.alloc().ioBuffer(0);
        }

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            byte[] input = new byte[data.readableBytes()];
            data.markReaderIndex();
            data.readBytes(input);
            data.resetReaderIndex();
            deflater.setInput(input);
            deflater.finish();

            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];

            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                if (count > 0) {
                    outputStream.write(buffer, 0, count);
                }
            }

            byte[] compressedBytes = outputStream.toByteArray();
            if (compressedBytes.length == 0 && input.length > 0) {
                ByteBuf result = ctx.alloc().ioBuffer(input.length);
                result.writeBytes(input);
                return result;
            }

            ByteBuf result = ctx.alloc().ioBuffer(compressedBytes.length);
            result.writeBytes(compressedBytes);
            return result;
        } finally {
            deflater.end();
        }
    }

    private static String debugPrefix(ByteBuf buf) {
        int len = Math.min(buf.readableBytes(), DEBUG_PREFIX_BYTES);
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            int value = buf.getByte(buf.readerIndex() + i) & 0xFF;
            if (i > 0) {
                sb.append(' ');
            }
            String hex = Integer.toHexString(value);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private static void debug(String message) {
        System.out.println("[NetEaseCompressionCodec] " + message);
    }
}
