SQL Storage implementation for DataScript

See [datascript/doc/storage.md](https://github.com/tonsky/datascript/blob/master/docs/storage.md)

# Using

Add this to `deps.edn`:

```clj
io.github.tonsky/datascript-storage-sql {:mvn/version "2.0.0"}
```

Create storage by passing in `javax.sql.DataSource` and `:dbtype` option:

```clj
(def datasource
  (doto (org.sqlite.SQLiteDataSource.)
    (.setUrl "jdbc:sqlite:target/db.sqlite")))

(def storage
  (storage-sql/make datasource
    {:dbtype :sqlite}))
 ```

You can also pass optional options:

```clj
(storage-sql/make datasource
  {:dbtype     :sqlite
   :batch-size 1000
   :table      "datascript"
   :ddl        "create table if not exists datascript (addr INTEGER primary key, content TEXT)"
   :freeze-str pr-str
   :thaw-str   clojure.edn/read-string})
```

Or use binary serialization:

```clj
(storage-sql/make datasource
  {:dbtype :sqlite
   
   :freeze-bytes
   (fn ^bytes [obj]
     (with-open [out (ByteArrayOutputStream.)]
       (t/write (t/writer out :msgpack) obj)
       (.toByteArray out)))
   
   :thaw-bytes
   (fn [^bytes b]
     (t/read
      (t/reader (ByteArrayInputStream. b) :msgpack)))})
```

After that, use it as any other storage:

```
(d/create-conn schema {:storage storage})
```

or

```
(d/store db storage)
```

Currently supported `:dbtype`-s:

- `:h2`
- `:mysql`
- `:postgresql`
- `:sqlite`

If your JDBC driver only provides you with `DataSource` and you want to add some basic pooling on top, use `storage-sql/pool`:

```
(def datasource
  (doto (SQLiteDataSource.)
    (.setUrl "jdbc:sqlite:target/db.sqlite")))

(def pooled-datasource
  (storage-sql/pool datasource
    {:max-conn      10
     :max-idle-conn 4}))

(def storage
  (storage-sql/make pooled-datasource
    {:dbtype :sqlite}))
```

`pool` takes non-pooled `DataSource` and returns new `DataSource` that pools connections for you.

If you used pool to create storage, you can close it this way:

```
(storage-sql/close storage)
```

## License

Copyright © 2023 Nikita Prokopov

Licensed under [MIT License](LICENSE).
