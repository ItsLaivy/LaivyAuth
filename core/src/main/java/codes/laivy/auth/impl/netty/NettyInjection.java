package codes.laivy.auth.impl.netty;

import codes.laivy.auth.impl.Mapping;
import io.netty.channel.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Flushable;
import java.util.*;

/**
 * This abstract class is responsible for packet injection and the handling of sending and receiving packets.
 * It is designed to be used with {@link Mapping} classes. It creates a pre-built injection system that only
 * requires the server's Channel.
 * <p>
 * This class works with low-level injections, allowing injections even before the player is identified,
 * enabling the analysis of the lowest handshake packets of the connection.
 * <p>
 * This class is abstract because it contains two abstract methods for monitoring the sent and received packets.
 */
public abstract class NettyInjection implements Flushable {

    // Static initializers

    private static final @NotNull Logger log = LoggerFactory.getLogger(NettyInjection.class);

    // Object

    private final @NotNull Object lock = new Object();
    private final @NotNull Map<@NotNull Channel, @NotNull ChannelHandler> handlers = new HashMap<>();

    private final @NotNull Channel channel;

    // Constructors

    /**
     * Constructor for NettyInjection.
     *
     * @param channel the channel to be injected.
     */
    protected NettyInjection(@NotNull Channel channel) {
        this.channel = channel;
    }

    // Getters

    /**
     * Gets the channel associated with this injection.
     *
     * @return the channel associated with this injection.
     */
    public final @NotNull Channel getChannel() {
        return channel;
    }

    // Modules

    /**
     * Injects a channel and starts monitoring its sent and received packets.
     *
     * @param channel the channel to be injected.
     */
    public final void inject(@NotNull Channel channel) {
        @NotNull ChannelDuplexHandler handler = new ChannelExecutorHandler(channel);

        synchronized (lock) {
            channel.pipeline().addBefore("packet_handler", "laivy_auth_" + channel.localAddress(), handler);
            handlers.put(channel, handler);
        }
    }

    /**
     * Ejects a channel from injection and stops monitoring its sent and received packets.
     *
     * @param channel the channel to be ejected.
     */
    public final void eject(@NotNull Channel channel) {
        synchronized (lock) {
            @Nullable ChannelHandler handler = handlers.remove(channel);
            if (handler != null) channel.pipeline().remove(handler);
        }
    }

    /**
     * Reads the data received by the channel. Return null to cancel the reception of the packet
     *
     * @param channel the channel that received the message.
     * @param context the channel handler context.
     * @param message the received message.
     * @return the processed message or null to cancel the reception.
     */
    public abstract @UnknownNullability Object read(@NotNull Channel channel, @NotNull ChannelHandlerContext context, @NotNull Object message);

    /**
     * Writes the data to be sent by the channel. Return null to cancel the sending of the packet.
     *
     * @param channel the channel that sends the message.
     * @param context the channel handler context.
     * @param message the message to be sent.
     * @param promise the channel promise.
     * @return the processed message or null to cancel the sending.
     */
    public abstract @UnknownNullability Object write(@NotNull Channel channel, @NotNull ChannelHandlerContext context, @NotNull Object message, @NotNull ChannelPromise promise);

    // Loaders

    /**
     * Flushes the handlers, removing all injected channels and their handlers.
     */
    @Override
    @SuppressWarnings("WhileLoopReplaceableByForEach")
    public final void flush() {
        synchronized (lock) {
            @NotNull Iterator<Channel> iterator = handlers.keySet().iterator();

            while (iterator.hasNext()) {
                @NotNull Channel channel = iterator.next();

                try {
                    @Nullable ChannelHandler handler = handlers.remove(channel);
                    if (handler != null) channel.pipeline().remove(handler);
                } catch (@NotNull NoSuchElementException ignore) {
                } catch (@NotNull Throwable throwable) {
                    log.error("Cannot unload {} channel: {}", channel.localAddress(), throwable.getMessage());
                    log.atDebug().setCause(throwable).log();
                }
            }
        }
    }

    // Implementations

    @Override
    public boolean equals(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof NettyInjection)) return false;
        @NotNull NettyInjection that = (NettyInjection) object;
        return Objects.equals(getChannel(), that.getChannel());
    }
    @Override
    public int hashCode() {
        return Objects.hashCode(getChannel());
    }

    @Override
    public @NotNull String toString() {
        return "NettyInjection{" +
                "channel=" + channel +
                '}';
    }

    // Classes

    /**
     * A handler for accepting new channels and injecting them upon activation.
     */
    private final class ChannelAcceptanceHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(@NotNull ChannelHandlerContext context, @NotNull Object message) throws Exception {
            if (message instanceof Channel) {
                @NotNull Channel channel = (Channel) message;

                channel.pipeline().addLast("laivy_auth_injector", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception {
                        @NotNull Channel channel = ctx.channel();
                        inject(channel);

                        super.channelActive(ctx);
                    }
                });
            }

            context.fireChannelRead(message);
        }
    }

    /**
     * A handler for executing read and write operations on the channel.
     */
    private final class ChannelExecutorHandler extends ChannelDuplexHandler {

        // Object

        private final @NotNull Channel channel;

        private ChannelExecutorHandler(@NotNull Channel channel) {
            this.channel = channel;
        }

        // Getters

        public @NotNull Channel getChannel() {
            return channel;
        }

        // Modules

        @Override
        public void channelRead(@NotNull ChannelHandlerContext context, @NotNull Object message) throws Exception {
            message = NettyInjection.this.read(getChannel(), context, message);
            if (message != null) super.channelRead(context, message);
        }

        @Override
        public void write(@NotNull ChannelHandlerContext context, @NotNull Object message, @NotNull ChannelPromise promise) throws Exception {
            message = NettyInjection.this.write(getChannel(), context, message, promise);
            if (message != null) super.write(context, message, promise);
        }
    }

}