package reactor.event.selector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Filter;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.internal.Utils;
import com.jayway.jsonpath.spi.JsonProvider;
import com.jayway.jsonpath.spi.MappingProvider;
import com.jayway.jsonpath.spi.Mode;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.*;

/**
 * @author Jon Brisbin
 */
public class JsonPathSelector extends ObjectSelector<JsonPath> {

	// Only need one of these
	private static ObjectMapper MAPPER = new ObjectMapper();
	private final Configuration jsonPathConfig;

	public JsonPathSelector(ObjectMapper mapper, String jsonPath, Filter... filters) {
		super(JsonPath.compile(jsonPath, filters));
		this.jsonPathConfig = Configuration.builder().jsonProvider(new Jackson2JsonProvider(mapper)).build();
	}

	public JsonPathSelector(String jsonPath, Filter... filters) {
		this(MAPPER, jsonPath, filters);
	}

	@Override
	public boolean matches(Object key) {
		if(null == key) {
			return false;
		}

		Object result = read(key);
		if(null == result) {
			return false;
		} else if(result instanceof List) {
			return ((List)result).size() > 0;
		} else if(result instanceof Map) {
			return !((Map)result).isEmpty();
		} else if(result instanceof JsonNode) {
			return ((JsonNode)result).size() > 0;
		} else {
			return true;
		}
	}

	private Object read(Object key) {
		if(key instanceof String) {
			return getObject().read((String)key, jsonPathConfig);
		} else if(key instanceof byte[]) {
			return getObject().read(new String((byte[])key), jsonPathConfig);
		} else {
			return getObject().read(key, jsonPathConfig);
		}
	}

	@SuppressWarnings("unchecked")
	private static class Jackson2JsonProvider implements JsonProvider, MappingProvider {
		private final ObjectMapper mapper;

		private Jackson2JsonProvider(ObjectMapper mapper) {
			this.mapper = mapper;
		}

		@Override
		public Mode getMode() {
			return Mode.STRICT;
		}

		@Override
		public Object parse(String json) throws InvalidJsonException {
			try {
				return mapper.readTree(json);
			} catch(IOException e) {
				throw new InvalidJsonException(e.getMessage(), e);
			}
		}

		@Override
		public Object parse(Reader jsonReader) throws InvalidJsonException {
			try {
				return mapper.readTree(jsonReader);
			} catch(IOException e) {
				throw new InvalidJsonException(e.getMessage(), e);
			}
		}

		@Override
		public Object parse(InputStream jsonStream) throws InvalidJsonException {
			try {
				return mapper.readTree(jsonStream);
			} catch(IOException e) {
				throw new InvalidJsonException(e.getMessage(), e);
			}
		}

		@Override
		public String toJson(Object obj) {
			try {
				return mapper.writeValueAsString(obj);
			} catch(JsonProcessingException e) {
				throw new IllegalStateException(e.getMessage(), e);
			}
		}

		@Override
		public Object createMap() {
			return new HashMap<String, Object>();
		}

		@Override
		public Iterable createArray() {
			return new LinkedList<Object>();
		}

		@Override
		public <T> T convertValue(Object fromValue,
		                          Class<T> toValueType)
				throws IllegalArgumentException {
			return mapper.convertValue(fromValue, toValueType);
		}

		@Override
		public <T extends Collection<E>, E> T convertValue(Object fromValue,
		                                                   Class<T> collectionType,
		                                                   Class<E> elementType)
				throws IllegalArgumentException {
			CollectionType collType = mapper.getTypeFactory().constructCollectionType(collectionType, elementType);
			return mapper.convertValue(fromValue, collType);
		}

		@Override
		public Object clone(Object model) {
			return Utils.clone((Serializable)model);
		}

		@Override
		public boolean isContainer(Object obj) {
			return isMap(obj) || isArray(obj);
		}

		@Override
		public boolean isArray(Object obj) {
			return (obj instanceof List || obj instanceof ArrayNode);
		}

		@Override
		public boolean isMap(Object obj) {
			return (obj instanceof Map || obj instanceof ObjectNode);
		}

		@Override
		public int length(Object obj) {
			if(obj instanceof List) {
				return ((List)obj).size();
			} else if(obj instanceof Map) {
				return ((Map)obj).size();
			} else if(obj instanceof ArrayNode) {
				return ((ArrayNode)obj).size();
			} else if(obj instanceof ObjectNode) {
				return ((ObjectNode)obj).size();
			} else {
				return 0;
			}
		}

		@Override
		public Iterable<Object> toIterable(Object obj) {
			if(obj instanceof List || obj instanceof JsonNode) {
				return (Iterable<Object>)obj;
			} else if(obj instanceof Map) {
				return ((Map)obj).values();
			} else {
				return Collections.emptyList();
			}
		}

		@Override
		public Collection<String> getPropertyKeys(Object obj) {
			if(obj instanceof Map) {
				return ((Map)obj).keySet();
			} else if(obj instanceof List) {
				List l = (List)obj;
				List<String> keys = new ArrayList<String>(l.size());
				for(Object o : l) {
					keys.add(String.valueOf(o));
				}
				return keys;
			} else if(obj instanceof ObjectNode) {
				ObjectNode node = (ObjectNode)obj;
				List<String> keys = new ArrayList<String>(node.size());
				Iterator<String> iter = node.fieldNames();
				while(iter.hasNext()) {
					keys.add(iter.next());
				}
				return keys;
			} else if(obj instanceof ArrayNode) {
				ArrayNode node = (ArrayNode)obj;
				List<String> keys = new ArrayList<String>(node.size());
				int len = node.size();
				for(int i = 0; i < len; i++) {
					keys.add(String.valueOf(node.get(i)));
				}
				return keys;
			}
			return Collections.emptyList();
		}

		@Override
		public Object getProperty(Object obj, Object key) {
			if(obj instanceof Map) {
				return ((Map)obj).get(key);
			} else if(obj instanceof List) {
				int idx = key instanceof Integer ? (Integer)key : Integer.parseInt(key.toString());
				return ((List)obj).get(idx);
			} else if(obj instanceof ObjectNode) {
				return unwrap(((ObjectNode)obj).get(key.toString()));
			} else if(obj instanceof ArrayNode) {
				int idx = key instanceof Integer ? (Integer)key : Integer.parseInt(key.toString());
				return unwrap(((ArrayNode)obj).get(idx));
			}
			return null;
		}

		@Override
		public void setProperty(Object obj, Object key, Object value) {
			if(obj instanceof Map) {
				((Map)obj).put(key, value);
			} else if(obj instanceof List) {
				int idx = key instanceof Integer ? (Integer)key : Integer.parseInt(key.toString());
				((List)obj).add(idx, value);
			} else if(obj instanceof ObjectNode) {
				((ObjectNode)obj).set(key.toString(), (JsonNode)value);
			} else if(obj instanceof ArrayNode) {
				int idx = key instanceof Integer ? (Integer)key : Integer.parseInt(key.toString());
				((ArrayNode)obj).set(idx, (JsonNode)value);
			}
		}

		private Object unwrap(JsonNode node) {
			if(node.isValueNode()) {
				if(node.isNumber()) {
					return node.numberValue();
				} else if(node.isTextual()) {
					return node.asText();
				} else {
					return node;
				}
			} else {
				return node;
			}
		}
	}

}
