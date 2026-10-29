package com.flippingfriend.companion;

import com.google.gson.Gson;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Disposable disk-backed indexes. Never opens an account database or survives verification. */
final class OfflineScratch implements AutoCloseable {
    private final Path file;
    private final Connection db;
    private final Gson json=new Gson();
    OfflineScratch(Path parent)throws Exception {
        file=Files.createTempFile(parent,"execution-index-",".sqlite");
        db=DriverManager.getConnection("jdbc:sqlite:"+file);
        try(var s=db.createStatement()) {
            s.execute("PRAGMA journal_mode=OFF");s.execute("PRAGMA synchronous=OFF");
            s.execute("PRAGMA cache_size=-8192");s.execute("PRAGMA temp_store=FILE");
            s.execute("CREATE TABLE kv (id INTEGER PRIMARY KEY, namespace TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, UNIQUE(namespace,key))");
        }
        db.setAutoCommit(false);
    }
    <K,V> Map<K,V> map(String name,Class<V> type) {return new DiskMap<>(name,type);}
    Set<String> set(String name) {
        Map<String,Boolean> map=map(name,Boolean.class);
        return new AbstractSet<String>() {
            public int size(){return map.size();}
            public boolean contains(Object key){return map.containsKey(key);}
            public boolean add(String key){return map.put(key,true)==null;}
            public Iterator<String> iterator(){throw new UnsupportedOperationException("Use membership, not materialization");}
            public boolean containsAll(Collection<?> keys){for(Object key:keys)if(!contains(key))return false;return true;}
        };
    }
    private final class DiskMap<K,V> extends AbstractMap<K,V> {
        final String name;final Class<V> type;
        DiskMap(String name,Class<V> type){this.name=name;this.type=type;}
        @Override public V get(Object key) {
            try(var s=db.prepareStatement("SELECT value FROM kv WHERE namespace=? AND key=?")) {
                s.setString(1,name);s.setString(2,json.toJson(key));
                try(var r=s.executeQuery()){return r.next()?json.fromJson(r.getString(1),type):null;}
            }catch(SQLException e){throw new IllegalStateException("Offline index read failed",e);}
        }
        @Override public boolean containsKey(Object key){return get(key)!=null;}
        @Override public V put(K key,V value) {
            V old=get(key);
            try(var s=db.prepareStatement("INSERT INTO kv(namespace,key,value) VALUES(?,?,?) ON CONFLICT(namespace,key) DO UPDATE SET value=excluded.value")) {
                s.setString(1,name);s.setString(2,json.toJson(key));s.setString(3,json.toJson(value));s.executeUpdate();return old;
            }catch(SQLException e){throw new IllegalStateException("Offline index write failed",e);}
        }
        @Override public int size() {
            try(var s=db.prepareStatement("SELECT count(*) FROM kv WHERE namespace=?")) {
                s.setString(1,name);try(var r=s.executeQuery()){r.next();return Math.toIntExact(r.getLong(1));}
            }catch(SQLException e){throw new IllegalStateException(e);}
        }
        @Override public Collection<V> values() {
            return new AbstractCollection<V>() {
                public int size(){return DiskMap.this.size();}
                public Iterator<V> iterator(){return new Iterator<V>() {
                    long cursor;V next;boolean ready;
                    public boolean hasNext() {
                        if(ready)return true;
                        try(var s=db.prepareStatement("SELECT id,value FROM kv WHERE namespace=? AND id>? ORDER BY id LIMIT 1")) {
                            s.setString(1,name);s.setLong(2,cursor);
                            try(var r=s.executeQuery()) {if(!r.next())return false;cursor=r.getLong(1);next=json.fromJson(r.getString(2),type);ready=true;return true;}
                        }catch(SQLException e){throw new IllegalStateException(e);}
                    }
                    public V next(){if(!hasNext())throw new NoSuchElementException();ready=false;return next;}
                };}
            };
        }
        @Override public Set<Entry<K,V>> entrySet(){throw new UnsupportedOperationException("Use ordered values");}
    }
    @Override public void close()throws Exception {
        try{db.close();}finally{Files.deleteIfExists(file);}
    }
}
