(ns mink.main
  (:require
   [cljs.closure :as cljsc]
   [clojure.edn :as edn]
   [clojure.string :as string]
   [mink.server :as ms]))

(defn start [& {:as opts}]
  (let [{:keys [host port ws-port]
         :or {host "localhost" 
              port 9000
              ws-port 9001}}
        opts]
    (ms/repl (merge {:host host :port port :ws-port ws-port} opts))))

(def cmd*
  {"-co"             :compile-opts
   "--compile-opts"  :compile-opts
   "-d"              :output-dir
   "--output-dir"    :output-dir
   "-re"             :repl-env
   "--repl-env"      :repl-env
   "-ro"             :repl-opts
   "--repl-opts"     :repl-opts
   "-t"              :target
   "--target"        :target
   ;; init options only for :main and :repl
   "-e"              :eval
   "--eval"          :eval
   "-i"              :init
   "--init"          :init
   "-v"              :verbose
   "--verbose"       :verbose
   ;; init options only for :compile
   "-O"              :optimizations
   "--optimizations" :optimizations
   "-o"              :output-to
   "--output-to"     :output-to
   "-w"              :watch
   "--watch"         :watch
   ;; main options
   "-"               :run-script
   "-c"              :compile
   "--compile"       :compile
   "-h"              :help
   "--help"          :help
   "-m"              :main
   "--main"          :main
   "-r"              :repl
   "--repl"          :repl
   "-s"              :serve
   "--serve"         :serve
   ;; extra compiler options
   "-ap"             :asset-path
   "--asset-path"    :asset-path
   "-bc"             :bundle-cmd
   "--bundle-cmd"    :bundle-cmd
   "-ca"             :checked-arrays
   "--checked-arrays" :checked-arrays
   "-ex"             :externs
   "--externs"       :externs
   "-fl"             :foreign-libs
   "--foreign-libs"  :foreign-libs
   "-gg"             :global-goog-object&array
   "--global-goog-object&array" :global-goog-object&array
   "-id"             :install-deps
   "--install-deps"  :install-deps
   "-md"             :modules
   "--modules"       :modules
   "-nd"             :npm-deps
   "--npm-deps"      :npm-deps
   "-pl"             :preloads
   "--preloads"      :preloads
   "-pp"             :pretty-print
   "--pretty-print"  :pretty-print
   "-sm"             :source-map
   "--source-map"    :source-map
   "-sn"             :stable-names
   "--stable-names"  :stable-names
   "-af"             :anon-fn-naming-policy
   "--anon-fn-naming-policy" :anon-fn-naming-policy
   "-ac"             :aot-cache
   "--aot-cache"     :aot-cache
   "-br"             :browser-repl
   "--browser-repl"  :browser-repl
   "-cn"             :cache-analysis
   "--cache-analysis" :cache-analysis
   "-cd"             :closure-defines
   "--closure-defines" :closure-defines
   "-ce"             :clojure-extra-annotations
   "--clojure-extra-annotations" :clojure-extra-annotations
   "-cc"             :clojure-output-charset
   "--clojure-output-charset" :clojure-output-charset
   "-cw"             :closure-warnings
   "--closure-warnings" :closure-warnings
   "-cs"             :compiler-stats
   "--compiler-stats" :compiler-stats
   "-dc"             :deps-cmd
   "--deps-cmd"      :deps-cmd
   "-es"             :elide-strict
   "--elide-strict"  :elide-strict
   "-fp"             :fingerprint
   "--fingerprint"   :fingerprint
   "-fi"             :fn-invoke-direct
   "--fn-invoke-direct" :fn-invoke-direct
   "-ha"             :hash-bang
   "--hash-bang"     :hash-bang
   "-ie"             :infer-externs
   "--infer-externs" :infer-externs
   "-li"             :language-in
   "--language-in"   :language-in
   "-lo"             :language-out
   "--language-out"  :language-out
   "-l"              :libs
   "--libs"          :libs
   "-nr"             :nodejs-rt
   "--nodejs-rt"     :nodejs-rt
   "-oc"             :optimize-constants
   "--optimize-constants" :optimize-constants
   "-ow"             :output-wrapper
   "--output-wrapper" :output-wrapper
   "-pj"             :package-json-resolution
   "--package-json-resolution" :package-json-resolution
   "-pb"             :parallel-build
   "--parallel-build" :parallel-build
   "-pr"             :preamble
   "--preamble"      :preamble
   "-pi"             :print-input-delimiter
   "--print-input-delimiter" :print-input-delimiter
   "-ps"             :process-shim
   "--process-shim"  :process-shim
   "-pn"             :psuedo-names
   "--psuedo-names"  :psuedo-names
   "-rd"             :recompile-dependents
   "--recompile-dependents" :recompile-dependents
   "-rp"             :rename-prefix
   "--rename-prefix" :rename-prefix
   "wp"              :rewrite-polyfills
   "--rewrite-polyfills" :rewrite-polyfills
   "-sa"             :source-map-asset-path
   "--source-map-asset-path" :source-map-asset-path
   "-sp"             :source-map-path
   "--source-map-path" :source-map-path
   "-st"             :source-map-timestamp
   "--source-map-timestamp" :source-map-timestamp
   "-sk"             :spec-skip-macros
   "--spec-skip-macros" :spec-skip-macros
   "-sf"             :static-fns
   "--static-fns"    :static-fns
   "-tf"             :target-fn
   "--target-fn"     :target-fn
   "-wa"             :warnings
   "--warnings"      :warnings
   "-wf"             :watch-fn
   "--watch-fn"      :watch-fn
   "-wh"             :warning-handlers
   "--warning-handlers" :warning-handlers
   ;; extra repl options
   "-an"             :analyze-path
   "--analyze-path"  :analyze-path
   "-de"             :def-emits-var
   "--def-emits-var" :def-emits-var
   "-rr"             :repl-requires
   "--repl-requires" :repl-requires
   "-rv"             :repl-verbose
   "--repl-verbose"  :repl-verbose
   "-wu"             :warn-on-undeclared
   "--warn-on-undeclared" :warn-on-undeclared
   ;; extra browser repl options
   "-lb"             :launch-browser
   "--launch-browser" :launch-browser
   "-wd"             :working-dir
   "--working-dir"   :working-dir
   "-sd"             :static-dir
   "--static-dir"    :static-dir
   "-sr"             :src
   "--src"           :src
   ;; node repl options
   "-ho"             :host
   "--host"          :host
   "-p"              :port
   "--port"          :port
   ;; websocket repl options
   "-wp"             :websocket-port
   "--websocket-port" :websocket-port})

(def cmd**
  (->> cmd*
       (mapv (fn [[k v]]
               [(if (= k "-")
                  :-
                  (some->> k rest (apply str) (str ":") edn/read-string))
                v]))
       (into {}) 
       (merge cmd*)))

(def commands
  (->> cmd**
       (mapv (fn [[_k v]]
               [v v]))
       (into {})
       (merge cmd**)))

(defn mapify-args [args]
  (->> args
       (partition-all 2 1)
       (mapv (fn [[k v]]
               (let [ldash? (string/starts-with? k "-")
                     lcolon? (string/starts-with? k ":")]
                 (if-not (or ldash? lcolon?)
                   nil
                   (let [dash? (string/starts-with? v "-")
                         colon? (string/starts-with? v ":")
                         fix-k (if ldash?
                                 (if (= ":-" k)
                                   :-
                                   (edn/read-string (str ":" (apply str (rest k)))))
                                 (edn/read-string k))]
                     (if (or dash? colon?)
                       [fix-k true]
                       [fix-k (edn/read-string v)]))))))
       (filter identity)
       (mapv (fn [[k v]]
               [(or (commands k) k) v]))
       (into {})))

(defn -main [& args]
  (let [ctx-map (mapify-args args)
        _known-opts (select-keys ctx-map cljsc/known-opts)]
    (start {:opts ctx-map #_ _known-opts :env-opts ctx-map})))
