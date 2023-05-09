package org.eclipse.pass.support.client;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonAdapter.Factory;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonReader.Token;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import jsonapi.Document;
import jsonapi.Document.IncludedSerialization;
import jsonapi.JsonApiFactory;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.eclipse.pass.support.client.adapter.AggregatedDepositStatusAdapter;
import org.eclipse.pass.support.client.adapter.AwardStatusAdapter;
import org.eclipse.pass.support.client.adapter.ContributorRoleAdapter;
import org.eclipse.pass.support.client.adapter.CopyStatusAdapter;
import org.eclipse.pass.support.client.adapter.DepositStatusAdapter;
import org.eclipse.pass.support.client.adapter.EventTypeAdapter;
import org.eclipse.pass.support.client.adapter.FileRoleAdapter;
import org.eclipse.pass.support.client.adapter.IntegrationTypeAdapter;
import org.eclipse.pass.support.client.adapter.PerformerRoleAdapter;
import org.eclipse.pass.support.client.adapter.SourceAdapter;
import org.eclipse.pass.support.client.adapter.SubmissionStatusAdapter;
import org.eclipse.pass.support.client.adapter.UriAdapter;
import org.eclipse.pass.support.client.adapter.UserRoleAdapter;
import org.eclipse.pass.support.client.adapter.ZonedDateTimeAdapter;
import org.eclipse.pass.support.client.model.Contributor;
import org.eclipse.pass.support.client.model.Deposit;
import org.eclipse.pass.support.client.model.File;
import org.eclipse.pass.support.client.model.Funder;
import org.eclipse.pass.support.client.model.Grant;
import org.eclipse.pass.support.client.model.Journal;
import org.eclipse.pass.support.client.model.PassEntity;
import org.eclipse.pass.support.client.model.Policy;
import org.eclipse.pass.support.client.model.Publication;
import org.eclipse.pass.support.client.model.Publisher;
import org.eclipse.pass.support.client.model.Repository;
import org.eclipse.pass.support.client.model.RepositoryCopy;
import org.eclipse.pass.support.client.model.Submission;
import org.eclipse.pass.support.client.model.SubmissionEvent;
import org.eclipse.pass.support.client.model.User;

/**
 * PassClient implementation using https://github.com/MarkoMilos/jsonapi.
 */
public class JsonApiPassClient implements PassClient {
    private final static String JSON_API_CONTENT_TYPE = "application/vnd.api+json";
    private final static MediaType JSON_API_MEDIA_TYPE = MediaType.parse("application/vnd.api+json; charset=utf-8");

    private final Moshi moshi;
    private final Moshi update_moshi;
    private final String baseUrl;
    private final OkHttpClient client;

    /**
     * Create a JsonApiClient.
     *
     * @param baseUrl base url of PASS API
     */
    public JsonApiPassClient(String baseUrl) {
        this(baseUrl, null, null);
    }

    /**
     * Create a JsonApiClient which uses HTTP basic auth.
     *
     * @param baseUrl base url of PASS API
     * @param user    user to connect as
     * @param pass    password of user
     */
    public JsonApiPassClient(String baseUrl, String user, String pass) {
        this.baseUrl = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "data/";

        OkHttpClient.Builder client_builder = new OkHttpClient.Builder();

        if (user != null && pass != null) {
            client_builder.addInterceptor(new OkHttpBasicAuthInterceptor(user, pass));
        }

        client = client_builder.build();
        moshi = create_moshi(false);

        // Serialize null value of attributes for the JSON API document
        update_moshi = create_moshi(true);
    }

    private Moshi create_moshi(boolean serialize_nulls) {
        Factory factory = new JsonApiFactory.Builder().addTypes(Contributor.class, Deposit.class, File.class,
                Funder.class, Grant.class, Journal.class, Policy.class, Publication.class, Publisher.class,
                Repository.class, RepositoryCopy.class, Submission.class, SubmissionEvent.class, User.class).build();

        Moshi.Builder builder = new Moshi.Builder().add(factory);

        if (serialize_nulls) {
            Factory serialize_nulls_factory = new JsonAdapter.Factory() {
                @Override
                public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
                    if (type.getTypeName().startsWith("org.eclipse.pass.")) {
                        return moshi.nextAdapter(this, type, annotations).serializeNulls();
                    }

                    return null;
                }
            };

            builder.add(serialize_nulls_factory);
        }

        builder.add(new AggregatedDepositStatusAdapter()).add(new AwardStatusAdapter())
                .add(new ContributorRoleAdapter()).add(new CopyStatusAdapter()).add(new DepositStatusAdapter())
                .add(new EventTypeAdapter()).add(new FileRoleAdapter()).add(new IntegrationTypeAdapter())
                .add(new PerformerRoleAdapter()).add(new SourceAdapter()).add(new SubmissionStatusAdapter())
                .add(new ZonedDateTimeAdapter()).add(new UriAdapter()).add(new UserRoleAdapter());

        return builder.build();
    }

    private String get_url(PassEntity obj) {
        return get_url(obj.getClass(), obj.getId());
    }

    private String get_url(Class<?> type, String id) {
        String name = get_json_type(type);

        if (id == null) {
            return baseUrl + name;
        } else {
            return baseUrl + name + "/" + id;
        }
    }

    private String get_json_type(Class<?> type) {
        String name = type.getSimpleName();

        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);

        return new String(chars);
    }

    private String get_java_type(String json_type) {
        char[] chars = json_type.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);

        return new String(chars);
    }

    @Override
    public <T extends PassEntity> void createObject(T obj) throws IOException {
        JsonAdapter<Document<T>> adapter = moshi.adapter(Types.newParameterizedType(Document.class, obj.getClass()));

        Document<T> doc = Document.with(obj).includedSerialization(IncludedSerialization.NONE).build();

        String json = adapter.toJson(doc);

        String url = get_url(obj);
        RequestBody body = RequestBody.create(json, JSON_API_MEDIA_TYPE);
        Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .addHeader("Content-Type", JSON_API_CONTENT_TYPE).post(body).build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException(
                    "Create failed: " + url + " returned " + response.code() + " " + response.body().string());
        }

        Document<T> result_doc = adapter.fromJson(response.body().string());
        obj.setId(result_doc.requireData().getId());
    }

    @Override
    public <T extends PassEntity> void updateObject(T obj) throws IOException {
        // Use adapters that will serialize null values for attributes
        JsonAdapter<Object> adapter = update_moshi.adapter(Types.newParameterizedType(Document.class, obj.getClass()));
        Document<T> doc = Document.with(obj).includedSerialization(IncludedSerialization.NONE).build();

        String json = adapter.toJson(doc);

        // Null relationships are not serialized. Add any missing null to one relationships
        json = add_null_relationships(json, get_null_relationships(obj));

        String url = get_url(obj);
        RequestBody body = RequestBody.create(json, JSON_API_MEDIA_TYPE);
        Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .addHeader("Content-Type", JSON_API_CONTENT_TYPE).patch(body).build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException(
                    "Update failed: " + url + " returned " + response.code() + " " + response.body().string());
        }
    }

    // Return all to one relationships that have a null value.
    private List<String> get_null_relationships(PassEntity entity) {
        List<String> rels = new ArrayList<>();

        for (Method m : entity.getClass().getMethods()) {
            if (m.getName().startsWith("get") && PassEntity.class.isAssignableFrom(m.getReturnType())) {
                try {
                    if (m.invoke(entity) == null) {
                        String rel = m.getName();
                        rel = Character.toLowerCase(rel.charAt(3)) + rel.substring(4);
                        rels.add(rel);
                    }
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new RuntimeException("Failed to invoke: " + m.getName(), e);
                }
            }
        }

        return rels;
    }

    // Add the missing to one relationships with null values to the document
    @SuppressWarnings("unchecked")
    private String add_null_relationships(String json, List<String> null_rels) throws IOException {
        try (Buffer in_buf = new Buffer();
                Buffer out_buf = new Buffer();
                JsonReader in = JsonReader.of(in_buf.writeUtf8(json));
                JsonWriter out = JsonWriter.of(out_buf);) {
            out.setSerializeNulls(true);

            in.beginObject();
            out.beginObject();

            while (in.hasNext()) {
                String name = in.nextName();

                if (name.equals("data")) {
                    in.beginObject();
                    out.name("data");
                    out.beginObject();
                    Map<String, ?> rels = null;

                    while (in.hasNext()) {
                        String data_name = in.nextName();

                        if (data_name.equals("relationships")) {
                            rels = (Map<String, ?>) in.readJsonValue();
                        } else {
                            out.name(data_name).jsonValue(in.readJsonValue());
                        }
                    }

                    if (rels == null) {
                        rels = new HashMap<>();
                    }

                    for (String rel: null_rels) {
                        rels.put(rel, null);
                    }

                    out.name("relationships").jsonValue(rels);

                    in.endObject();
                    out.endObject();
                } else {
                    out.name(name).jsonValue(in.readJsonValue());
                }
            }
            in.endObject();
            out.endObject();

            return out_buf.readUtf8();
        }
    }

    private static class Relationship {
        String name;
        List<String> targets;
        String target_type;
        boolean to_many;

        Relationship(String name) {
            this.name = name;
            this.targets = new ArrayList<>();
        }
    }

    // Return map of source object id to object relationships.
    // Ignore any relationships whose target is included
    private Map<String, List<Relationship>> get_relationships(String json_api_doc) throws IOException {
        Map<String, List<Relationship>> result = new HashMap<>();

        // Contains type_id for objects which are included in the document
        Set<String> included = new HashSet<>();

        try (Buffer buffer = new Buffer(); JsonReader reader = JsonReader.of(buffer.writeUtf8(json_api_doc))) {
            reader.beginObject();

            while (reader.hasNext()) {
                String top_name = reader.nextName();

                if (top_name.equals("data")) {
                    Token next = reader.peek();

                    if (next == Token.BEGIN_ARRAY) {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            gather_relationships_from_data(result, reader, included);
                        }
                        reader.endArray();
                    } else if (next == Token.BEGIN_OBJECT) {
                        gather_relationships_from_data(result, reader, included);
                    } else {
                        reader.skipValue();
                    }
                } else if (top_name.equals("included")) {
                    reader.beginArray();

                    while (reader.hasNext()) {
                        String id = null;
                        String type = null;

                        reader.beginObject();
                        while (reader.hasNext()) {
                            switch (reader.nextName()) {
                                case "id":
                                    id = reader.nextString();
                                    break;

                                case "type":
                                    type = reader.nextString();
                                    break;

                                default:
                                    reader.skipValue();
                                    break;
                            }
                        }
                        reader.endObject();

                        if (id != null && type != null) {
                            included.add(type + "_" + id);
                        }
                    }

                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }

            reader.endObject();
        }

        // Prune relationship targets that are included in the document
        if (included.size() > 0) {
            result.forEach((id, rels) -> {
                rels.forEach(rel -> {
                    rel.targets.removeIf(target_id -> included.contains(rel.target_type + "_" + target_id));
                });
            });
        }

        return result;
    }

    // Return relationships from a data object
    private void gather_relationships_from_data(Map<String, List<Relationship>> result, JsonReader reader,
            Set<String> included) throws IOException {
        String id = null;
        List<Relationship> rels = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();

            if (name.equals("relationships")) {
                rels = parse_relationships(reader, included);
            } else if (name.equals("id")) {
                id = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        if (id != null && rels != null && rels.size() > 0) {
            result.put(id, rels);
        }
    }

    // Parse the relationships object
    private List<Relationship> parse_relationships(JsonReader reader, Set<String> included) throws IOException {
        List<Relationship> result = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            Relationship rel = new Relationship(reader.nextName());

            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("data")) {
                    Token next = reader.peek();

                    if (next == Token.BEGIN_ARRAY) {
                        reader.beginArray();
                        rel.to_many = true;

                        while (reader.hasNext()) {
                            fill_relationship(rel, reader);
                        }

                        reader.endArray();
                    } else if (next == Token.BEGIN_OBJECT) {
                        rel.to_many = false;
                        fill_relationship(rel, reader);
                    } else {
                        reader.skipValue();
                    }

                    if (rel.targets.size() > 0) {
                        result.add(rel);
                    }
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        reader.endObject();

        return result;
    }

    // Parse the data of a relationship target into a Relationship
    private void fill_relationship(Relationship rel, JsonReader reader) throws IOException {
        reader.beginObject();

        String id = null;
        String type = null;

        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "id":
                    id = reader.nextString();
                    break;

                case "type":
                    type = reader.nextString();
                    break;

                default:
                    reader.skipValue();
                    break;
            }
        }

        if (id != null && type != null) {
            rel.targets.add(id);
            rel.target_type = type;
        }

        reader.endObject();
    }

    // Create a PassEntity and set the id. It must have an appropriate constructor.
    private Object create_target(String target_id, String class_name) {
        try {
            return Class.forName(class_name).getConstructor(String.class).newInstance(target_id);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to create: " + class_name, e);
        }
    }

    // Set a value on an object using a set method.
    private void set_value(Object obj, String set_method, Object value) {
        try {
            Class<?> value_class = value.getClass();

            // Handle the set method taking a List instead of a List implementation
            if (value instanceof List) {
                value_class = List.class;
            }

            obj.getClass().getMethod(set_method, value_class).invoke(obj, value);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke: " + set_method, e);
        }
    }

    // Set a relationship on a matched object
    private void set_relationship(Object obj, Relationship rel) {
        // Targets may have been pruned
        if (rel.targets.size() == 0) {
            return;
        }

        String target_class_name = "org.eclipse.pass.support.client.model." + get_java_type(rel.target_type);
        Object target;

        if (rel.to_many) {
            List<Object> list = new ArrayList<>();
            rel.targets.forEach(id -> {
                list.add(create_target(id, target_class_name));
            });
            target = list;
        } else {
            target = create_target(rel.targets.get(0), target_class_name);
        }

        set_value(obj, "set" + get_java_type(rel.name), target);
    }

    private void set_relationships(Object obj, List<Relationship> rels) {
        if (rels != null) {
            rels.forEach(rel -> {
                set_relationship(obj, rel);
            });
        }
    }

    @Override
    public <T extends PassEntity> T getObject(Class<T> type, String id, String... include) throws IOException {
        JsonAdapter<Document<T>> adapter = moshi.adapter(Types.newParameterizedType(Document.class, type));

        HttpUrl.Builder url_builder = HttpUrl.parse(get_url(type, id)).newBuilder();
        if (include != null && include.length > 0) {
            url_builder.addQueryParameter("include", String.join(",", include));
        }
        HttpUrl url = url_builder.build();

        Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .addHeader("Content-Type", JSON_API_CONTENT_TYPE).get().build();

        Response response = client.newCall(request).execute();

        if (response.code() == 404) {
            return null;
        }

        String body = response.body().string();

        if (!response.isSuccessful()) {
            throw new IOException("Get failed: " + url + " returned " + response.code() + " " + body);
        }

        Document<T> doc = adapter.fromJson(body);
        T result = doc.requireData();

        set_relationships(result, get_relationships(body).get(id));

        return result;
    }

    @Override
    public <T extends PassEntity> void deleteObject(Class<T> type, String id) throws IOException {
        String url = get_url(type, id);

        Request request = new Request.Builder().url(url).delete().build();
        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException(
                    "Delete failed: " + url + " returned " + response.code() + " " + response.body().string());
        }
    }

    @Override
    public <T extends PassEntity> PassClientResult<T> selectObjects(PassClientSelector<T> selector) throws IOException {
        JsonAdapter<Document<List<T>>> adapter = moshi.adapter(
                Types.newParameterizedType(Document.class, Types.newParameterizedType(List.class, selector.getType())));
        HttpUrl.Builder url_builder = HttpUrl.parse(get_url(selector.getType(), null)).newBuilder();

        String[] include = selector.getInclude();
        if (include != null && include.length > 0) {
            url_builder.addQueryParameter("include", String.join(",", include));
        }

        if (selector.getFilter() != null) {
            url_builder.addQueryParameter("filter", selector.getFilter());
        }

        if (selector.getSorting() != null) {
            url_builder.addQueryParameter("sort", selector.getSorting());
        }

        url_builder.addQueryParameter("page[offset]", "" + selector.getOffset());
        url_builder.addQueryParameter("page[limit]", "" + selector.getLimit());
        url_builder.addQueryParameter("page[totals]", null);

        HttpUrl url = url_builder.build();

        Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .addHeader("Content-Type", JSON_API_CONTENT_TYPE).get().build();

        Response response = client.newCall(request).execute();

        if (response.code() == 404) {
            return null;
        }

        String body = response.body().string();

        if (!response.isSuccessful()) {
            throw new IOException("Select failed: " + url + " returned " + response.code() + " " + body);
        }

        Document<List<T>> doc = adapter.fromJson(body);
        List<T> matches = doc.requireData();
        long total = -1;

        if (doc.getMeta().has("page")) {
            Map<?, ?> page = (Map<?, ?>) doc.getMeta().get("page");

            if (page.containsKey("totalRecords")) {
                total = ((Double) page.get("totalRecords")).longValue();
            }
        }

        Map<String, List<Relationship>> rels = get_relationships(body);

        matches.forEach(o -> {
            set_relationships(o, rels.get(o.getId()));
        });

        return new PassClientResult<>(matches, total);
    }
}