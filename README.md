SQL Storage implementation for DataScript

See [datascript/doc/storage.md](https://github.com/tonsky/datascript/blob/master/docs/storage.md)

# Using

Add this to `deps.edn`:

```clj
io.github.tonsky/datascript-storage-sql {:mvn/version "0.1.0"}
```

Create storage by passing in `java.sql.Connection` and `:dbtype` option:

```clj
(def conn
  (DriverManager/getConnection "jdbc:sqlite:target/db.sqlite"))

(def storage
  (storage-sql/make conn
    {:dbtype :sqlite}))
 ```

You can also pass optional options:

```clj
(storage-sql/make conn
  {:dbtype     :sqlite
   :batch-size 1000
   :table      "datascript"
   :ddl        "create table if not exists datascript (addr INTEGER primary key, content TEXT)"
   :freeze-str pr-str
   :thaw-str   clojure.edn/read-string})
```

Or use binary serialization:

```clj
(storage-sql/make conn
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

- `:sqlite`

If needed, you can close connection through storage:

```
(storage-sql/close storage)
```

## License

Copyright © 2023 Nikita Prokopov

Licensed under [MIT License](LICENSE).
