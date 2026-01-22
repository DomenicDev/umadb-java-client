package io.umadb.client.grpc;

import io.umadb.client.*;
import umadb.v1.Umadb;

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

        return eventBuilder.build();
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
        if (readRequest.subscribe() != null) {
            readRequestBuilder.setSubscribe(readRequest.subscribe());
        }
        if (readRequest.batchSize() != null) {
            readRequestBuilder.setBatchSize(readRequest.batchSize());
        }
        return readRequestBuilder.build();
    }

    public static ReadResponse toReadResponse(Umadb.ReadResponse umadbReadResponse) {
        var sequencedEvents = umadbReadResponse.getEventsList().stream().map(UmaDbUtils::toSequencedEvent).toList();
        return new ReadResponse(
                sequencedEvents,
                umadbReadResponse.getHead()
        );
    }

    public static SequencedEvent toSequencedEvent(Umadb.SequencedEvent umadbSequencedEvent) {
        return new SequencedEvent(
                umadbSequencedEvent.getPosition(),
                toEvent(umadbSequencedEvent.getEvent())
        );
    }

    public static Event toEvent(Umadb.Event umadbEvent) {
        return new Event(
                umadbEvent.getEventType(),
                umadbEvent.getTagsList(),
                umadbEvent.getData().toByteArray(),
                isNullOrBlank(umadbEvent.getUuid()) ? null : UUID.fromString(umadbEvent.getUuid())
        );
    }

    private static boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }

}
