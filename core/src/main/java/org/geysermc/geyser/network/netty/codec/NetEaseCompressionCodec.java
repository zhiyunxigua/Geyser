/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

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

        // 只处理网易前缀，不解压
        if (compressed.readableBytes() >= 1) {
            int firstByte = compressed.getByte(compressed.readerIndex()) & 0xFF;
            if (firstByte == 0xFE) {
                // 跳过 0xFE 前缀
                ByteBuf stripped = compressed.slice(1, compressed.readableBytes() - 1);

                // 检查第二个字节
                if (stripped.readableBytes() >= 1) {
                    int secondByte = stripped.getByte(stripped.readerIndex()) & 0xFF;
                    if (secondByte == 0x00) {
                        // 压缩数据：需要解压，但设置正确的算法标记
                        ByteBuf dataToDecompress = stripped.slice(1, stripped.readableBytes() - 1);
                        ByteBuf decompressed = decompressWithZlibRaw(ctx, dataToDecompress);

                        // 关键：设置算法为 NONE，因为数据已经解压了
                        // BedrockBatchDecoder 会直接处理
                        msg.setAlgorithm(PacketCompressionAlgorithm.NONE);
                        msg.setUncompressed(decompressed);
                    } else {
                        // 未压缩：直接使用 stripped 的内容
                        ByteBuf uncompressed = stripped.slice();
                        msg.setAlgorithm(PacketCompressionAlgorithm.NONE);
                        msg.setUncompressed(uncompressed.retain());
                    }
                }

                out.add(msg.retain());
                return;
            }
        }

        // 非网易数据：调用父类标准处理
        super.decode(ctx, msg, out);
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
}
