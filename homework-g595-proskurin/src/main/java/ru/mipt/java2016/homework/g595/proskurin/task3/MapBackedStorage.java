package ru.mipt.java2016.homework.g595.proskurin.task3;
import ru.mipt.java2016.homework.base.task2.KeyValueStorage;
import ru.mipt.java2016.homework.g595.proskurin.task3.MySerializer;

import java.io.IOException;
import java.util.Iterator;
import javafx.util.Pair;
import sun.misc.Cache;

import java.io.*;
import java.util.HashMap;
import java.util.ArrayList;

public class MapBackedStorage<K, V> implements KeyValueStorage<K, V> {

    private static final int MAX_CACHE_SIZE = 9;
    private static final int MAX_PART = 3;

    private MySerializer<K> keySerializer;
    private MySerializer<V> valueSerializer;
    private boolean closed;
    private HashMap<K, Integer> myMap = new HashMap<K, Integer>();
    private String realPath;
    private RandomAccessFile base;
    private RandomAccessFile inout;
    private RandomAccessFile temp;
    private String theirPath;
    private ArrayList<Pair<K, V> > cache = new ArrayList<Pair<K, V>>();
    private int maxSize = 0;

    private void isClosed() {
        if (closed) {
            throw new IllegalStateException("Data base is already closed!");
        }
    }

    private void addToCache(K key, V value) {
        if (cache.size() == MAX_CACHE_SIZE) {
            cache.remove(0);
        }
        cache.add(new Pair<K, V>(key, value));
    }

    private V getCache(K key) {
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).getKey() == key) {
                return cache.get(i).getValue();
            }
        }
        return null;
    }

    private void deleteFromCache(K key) {
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).getKey() == key) {
                cache.remove(i);
                return;
            }
        }
    }

    void update() {
        if (myMap.size() > maxSize) {
            maxSize = myMap.size();
        }
    }

    private void rebuild() {
        if (myMap.size() >= maxSize / MAX_PART) {
            return;
        }
        try {
            maxSize = myMap.size();
            temp.seek(0);
            int len = myMap.size();
            for (HashMap.Entry<K, Integer> item : myMap.entrySet()) {
                K key = item.getKey();
                V value = read(key);
                keySerializer.output(temp, key);
                valueSerializer.output(temp, value);
            }
            myMap.clear();
            temp.seek(0);
            base.seek(0);
            for (int i = 0; i < len; i++) {
                K key = keySerializer.input(temp);
                V value = valueSerializer.input(temp);
                myMap.put(key, (int) base.getFilePointer());
                valueSerializer.output(base, value);
            }
        } catch (IOException err) {
            System.out.println("Some error occured!");
        }
    }

    public MapBackedStorage(String path, MySerializer<K> keySerializer,
                             MySerializer<V> valueSerializer) throws IOException {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        closed = false;
        theirPath = path;
        realPath = path + File.separator + "database.txt";
        base = new RandomAccessFile(realPath, "rw");
        inout = new RandomAccessFile(path + File.separator + "info.txt", "rw");
        temp = new RandomAccessFile(path + File.separator + "temp.txt", "rw");
        try {
            if (inout.length() == 0) {
                return;
            }
            IntegerSerializer serInt = new IntegerSerializer();
            int len = serInt.input(inout);
            for (int i = 0; i < len; i++) {
                K key = keySerializer.input(inout);
                Integer shift = serInt.input(inout);
                myMap.put(key, shift);
            }
        } catch (IOException err) {
            System.out.println("Input/Output error occured!");
        }
    }

    @Override
    public Iterator<K> readKeys() {
        update();
        rebuild();
        isClosed();
        return myMap.keySet().iterator();
    }

    @Override
    public boolean exists(K key) {
        update();
        rebuild();
        isClosed();
        return myMap.containsKey(key);
    }

    @Override
    public void close() {
        update();
        rebuild();
        isClosed();
        try {
            inout.seek(0);
            IntegerSerializer serInt = new IntegerSerializer();
            serInt.output(inout, myMap.size());
            for (HashMap.Entry<K, Integer> item : myMap.entrySet()) {
                keySerializer.output(inout, item.getKey());
                serInt.output(inout, item.getValue());
            }
            myMap.clear();
            inout.close();
            base.close();
            temp.close();
            closed = true;
        } catch (IOException err) {
            System.out.println("Input/Output error occured!");
        }
    }

    @Override
    public int size() {
        update();
        rebuild();
        isClosed();
        return myMap.size();
    }

    @Override
    public void delete(K key) {
        update();
        rebuild();
        isClosed();
        deleteFromCache(key);
        myMap.remove(key);
    }

    @Override
    public void write(K key, V value) {
        update();
        rebuild();
        isClosed();
        try {
            base.seek(base.length());
            myMap.put(key, (int) base.length());
            valueSerializer.output(base, value);
        } catch (IOException err) {
            System.out.println("Some error occured!");
        }
    }

    @Override
    public V read(K key) {
        update();
        rebuild();
        isClosed();
        V tmp = getCache(key);
        if (tmp != null) {
            return tmp;
        }
        if (myMap.containsKey(key)) {
            Integer shift = myMap.get(key);
            try {
                base.seek((long) shift);
                tmp = valueSerializer.input(base);
                addToCache(key, tmp);
                return tmp;
            } catch (IOException err) {
                System.out.println("Some error occured!");
            }
        }
        return null;
    }

}