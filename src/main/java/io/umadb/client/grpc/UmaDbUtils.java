package io.umadb.client.grpc;

import io.umadb.client.*;
import umadb.v1.Umadb;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.google.protobuf.ByteString.copyFrom;

public final class UmaDbUtils {

    private UmaDbUtils() {
        // utility class
    }

    public static Umadb.AppendRequest toUmadbAppendRequest(AppendRequest appendRequest) {
        var eventsToAppend = appendRequest.events().stream().map(UmaDbUtils::toUmadbEvent).toList();
        var appendRequestBuilder = Umadb.AppendRequest.newBuilder()
                .addAllEvents(eventsToAppend);

        if (appendRequest.condition() != null) {
            appendRequestBuilder.setCondition(UmaDbUtils.toUmadbAppendCondition(appendRequest.condition()));
        }

        if (appendRequest.trackingInfo() != null) {
            appendRequestBuilder.setTrackingInfo(toUmadbTrackingInfo(appendRequest.trackingInfo()));
        }

        return appendRequestBuilder.build();
    }

    public static Umadb.Event toUmadbEvent(Event event) {
        var eventBuilder = Umadb.Event.newBuilder()
                .setEventType(event.type())
                .addAllTags(event.tags())
                .setData(copyFrom(event.data()));

        if (event.id() != null) {
            eventBuilder.setUuid(event.id().toString());
        }

        event.metadata().forEach((key, value) -> eventBuilder.addMetadata(
                Umadb.MetadataEntry.newBuilder()
                        .setKey(key)
                        .setValue(value)
                        .build()
        ));

        return eventBuilder.build();
    }

    public static Umadb.TrackingInfo toUmadbTrackingInfo(TrackingInfo trackingInfo) {
        return Umadb.TrackingInfo.newBuilder()
                .setSource(trackingInfo.source())
                .setPosition(trackingInfo.position())
                .build();
    }

    public static Umadb.AppendCondition toUmadbAppendCondition(AppendCondition appendCondition) {
        var umaDbQuery = toUmadbQuery(appendCondition.failIfEventsMatch());
        var builder = Umadb.AppendCondition.newBuilder()
                .setFailIfEventsMatch(umaDbQuery);

        if (appendCondition.after() != null) {
            builder.setAfter(appendCondition.after());
        }
        return builder.build();
    }

    public static Umadb.Query toUmadbQuery(Query query) {
        var queryItems = query.items().stream().map(UmaDbUtils::toUmadbQueryItem).toList();
        return Umadb.Query.newBuilder()
                .addAllItems(queryItems)
                .build();
    }

    public static Umadb.QueryItem toUmadbQueryItem(QueryItem queryItem) {
        return Umadb.QueryItem.newBuilder()
                .addAllTypes(queryItem.types())
                .addAllTags(queryItem.tags())
                .build();
    }

    public static Umadb.ReadRequest toUmadbReadRequest(ReadRequest readRequest) {
        var readRequestBuilder = Umadb.ReadRequest.newBuilder();
        if (readRequest.query() != null) {
            readRequestBuilder.setQuery(toUmadbQuery(readRequest.query()));
        }
        if (readRequest.start() != null) {
            readRequestBuilder.setStart(readRequest.start());
        }
        if (readRequest.backwards() != null) {
            readRequestBuilder.setBackwards(readRequest.backwards());
        }
        if (readRequest.limit() != null) {
            readRequestBuilder.setLimit(readRequest.limit());
        }
        if (readRequest.batchSize() != null) {
            readRequestBuilder.setBatchSize(readRequest.batchSize());
        }
        return readRequestBuilder.build();
    }

    public static Umadb.SubscribeRequest toUmadbSubscribeRequest(SubscribeRequest subscribeRequest) {
        var builder = Umadb.SubscribeRequest.newBuilder();
        if (subscribeRequest.query() != null) {
            builder.setQuery(toUmadbQuery(subscribeRequest.query()));
        }
        if (subscribeRequest.after() != null) {
            builder.setAfter(subscribeRequest.after());
        }
        if (subscribeRequest.batchSize() != null) {
            builder.setBatchSize(subscribeRequest.batchSize());
        }
        return builder.build();
    }

    public static Umadb.TrackingRequest toUmadbTrackingRequest(String source) {
        return Umadb.TrackingRequest.newBuilder()
                .setSource(source)
                .build();
    }

    public static ReadResponse toReadResponse(Umadb.ReadResponse umadbReadResponse) {
        var sequencedEvents = umadbReadResponse.getEventsList().stream().map(UmaDbUtils::toSequencedEvent).toList();
        return new ReadResponse(
                sequencedEvents,
                umadbReadResponse.getHead()
        );
    }

    public static SubscribeResponse toSubscribeResponse(Umadb.SubscribeResponse umadbSubscribeResponse) {
        var sequencedEvents = umadbSubscribeResponse.getEventsList().stream().map(UmaDbUtils::toSequencedEvent).toList();
        return new SubscribeResponse(sequencedEvents);
    }

    public static SequencedEvent toSequencedEvent(Umadb.SequencedEvent umadbSequencedEvent) {
        return new SequencedEvent(
                umadbSequencedEvent.getPosition(),
                toEvent(umadbSequencedEvent.getEvent()),
                umadbSequencedEvent.hasTrackingInfo()
                        ? toTrackingInfo(umadbSequencedEvent.getTrackingInfo())
                        : null
        );
    }

    public static TrackingInfo toTrackingInfo(Umadb.TrackingInfo umadbTrackingInfo) {
        return new TrackingInfo(
                umadbTrackingInfo.getSource(),
                umadbTrackingInfo.getPosition()
        );
    }

    public static Event toEvent(Umadb.Event umadbEvent) {
        return new Event(
                umadbEvent.getEventType(),
                umadbEvent.getTagsList(),
                umadbEvent.getData().toByteArray(),
                isNullOrBlank(umadbEvent.getUuid()) ? null : UUID.fromString(umadbEvent.getUuid()),
                toMetadata(umadbEvent)
        );
    }

    /**
     * Collapses the wire-level repeated metadata entries into a map.
     * <p>
     * The proto permits duplicate keys; when they occur the last entry wins.
     */
    private static Map<String, String> toMetadata(Umadb.Event umadbEvent) {
        var metadata = new LinkedHashMap<String, String>();
        umadbEvent.getMetadataList().forEach(entry -> metadata.put(entry.getKey(), entry.getValue()));
        return metadata;
    }

    private static boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }

}
