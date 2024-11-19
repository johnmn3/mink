# repl.ws

It's a websocket repl.

## Gist

It can do repls, socket repls and socket prepls using a websocket to the browser. Like so:

### normal repl

```shell
$ clj -M -m cljs.main -re ws -w src
```

`open index.html`

```clojure
Waiting for connection at ws://localhost:9001 ...
ClojureScript 1.11.132
cljs.user=> (+ 1 2)
3
```

### socket repl

```shell
$ clj -J-Dclojure.server.repl="{:port 5555 :accept cljs.server.ws/repl}"
```

Then in another terminal:

```shell
$ rlwrap nc localhost 5555
Waiting for connection at ws://localhost:9001 ...
```

`open index.html`

```clojure
Watch compilation log available at: out/watch.log
ClojureScript 1.11.132
cljs.user=> (+ 1 2)
3
```

### socket prepl

```shell
$ clj -J-Dclojure.server.prepl="{:port 5555 :accept cljs.server.ws/prepl}"
```

Then in another terminal:

```shell
$ rlwrap nc localhost 5556
{:tag :out, :val "Waiting for connection at ws://localhost:9001 ...\n"}
```

`open index.html`

```clojure
{:tag :out, :val "\ncljs.user:=> "}
(+ 1 2)
{:tag :ret, :val "3", :ns "cljs.user", :ms 10, :form "(+ 1 2)"}
```

## Usage

As an example, in an example Clojurescript project called `ex` and with a `deps.edn` like this:

```clojure
{:path ["src"]
 :deps {org.clojure/clojurescript {:mvn/version "1.11.132"}
        ,,,
        io.github.johnmn3/repl.ws {:git/url "https://github.com/johnmn3/repl.ws"
                                   :sha "sha-here"
                                   #_#_:exclusions [org.slf4j/slf4j-nop]}}}
 ;; uncomment to exclude slf4j-nop ^^^^ in order to enable slf4j logs
```

And a `src/ex/core.cljs` like this:

```clojure
(ns ex.core
  (:require [clojure.browser.ws :as ws]))

(when-not (ws/alive?)
  (ws/connect "ws://localhost:9001"))
```

And with an `index.html` like this:

```html
<html>
  <body>
    <div id="app">
      <script src="out/goog/base.js"></script>
      <script src="out/main.js"></script>
      <script type="text/javascript">
      goog.require('ex.core');
      </script>
    </div>
  </body>
</html>
```

Then you can compile it:

```shell
clj -M -m cljs.main -c ex.core
```

Launching a repl will also compile the project:

```clojure
$ clj -M -m cljs.main -re ws
Waiting for connection at ws://localhost:9001 ...
```

Then `open index.html` in a browser and when it connects to the repl server it will drop you into a repl:

```clojure
$ clj -M -m cljs.main -re ws
Waiting for connection at ws://localhost:9001 ...
ClojureScript 1.11.132
cljs.user=> (+ 1 2)
3
```

## Details

Thanks to the genius of David Nolen, Clojurescript repls are mostly open ended - anyone can add a repl environment from an external library.

Here's how you would normally start a node repl in vanilla Clojurescript:

```shell
clj -M -main cljs.main --repl-env node --compile hello-world.core --repl
```

Or, more succinctly (`node` dosn't actually need the `-r`):

```shell
clj -M -m cljs.main -re node -c hello-world.core
```

And here's how you would normally start a vanilla repl in a browser (`-re browser` is the default implicit env):

```shell
clj -M -m cljs.main -c hello-world.core -r
```

With `repl.ws` you can do similarly:

```shell
clj -M -m cljs.main -re ws -w src
```

`-w src` means watch the `src` directory for changes. Files will recompile, the browser will reload and the repl will reconnect when you save a file. Like with `node`, you don't need to pass the `-r` flag as a repl is presumed (otherwise you'd probably just use the vanilla repls - but PRs/issues/forks welcome).

## How?

`repl.ws` quazi-vendors the `cljs.repl.ws` and `cljs.server.ws` namespaces, allowing the cljs.main machinery to do all the work.

When you pass a `-re blah` flag to `cljs.main`, it will look for a namespace called `cljs.repl.blah` and use it just like it would natively for `cljs.repl.node` or `cljs.repl.browser`. This makes the implementation quite simple. Whether `repl.ws` will be able to use all of `cljs.main`'s flags is TBD.

## repl switching

Multiple clients can connect to the same repl server.

`repl.ws/list` will return a list of the currently connected clients and `repl.ws/=>N` will switch evaluation to the client with the given index.

```clojure
cljs.user=>                 ;; open index.html in browser tab
repl client: :repl.ws/=>1
cljs.user=>                 ;; open index.html in another browser tab
repl client: :repl.ws/=>2
cljs.user=>                 ;; open index.html in yet another browser tab
repl client: :repl.ws/=>3
cljs.user=> :repl.ws/list
:repl.ws=>1
*:repl.ws=>2
:repl.ws=>3
cljs.user=> 
cljs.user=> (.log js/console "hi")
nil
cljs.user=> :repl.ws/=>1
:repl.ws/1=> 
cljs.user=> (.log js/console "hi")
nil
cljs.user=> :repl.ws/=>3
:repl.ws/3=> 
cljs.user=> (.log js/console "hi")
nil
cljs.user=> :repl.ws/list
:repl.ws=>1
:repl.ws=>2
*:repl.ws=>3
```

## socket repl/prepl

First launch a socket repl:

`clj -J-Dclojure.server.repl="{:port 5555 :accept cljs.server.ws/repl}"`

Then, in another terminal, connect:

```clojure
 $ rlwrap nc localhost 5555
Waiting for connection at ws://localhost:9001 ...
```

Then, in a browser, load _the actual file_:

`open ./index.html`

The second terminal prompt should return:

```clojure
 $ rlwrap nc localhost 5555
Waiting for connection at ws://localhost:9001 ...
ClojureScript 1.10.XXX
cljs.user=>
```

Print to the browser:

```clojure
cljs.user=> (js/console.log "hi")
nil
```

Check the browser and you should see the "hi" in the console.

Now open another tab with same file. You'll see information added to the terminal:

```clojure
new repl client: :repl.ws/=>2
cljs.user:1=>
```

Evaluate `:repl.ws/=>2` at the repl to switch the repl to the new client:

```clojure
cljs.user=> :repl.ws/=>2
switching to client: 2
nil
cljs.user:2=>
```

Console printing will now show up in the second browser window.

## party repls!

Now, open a third terminal and connect to the socket server again - multiple repls can swith to the same client!

```clojure
$ rlwrap nc localhost 5555
ClojureScript 1.10.XXX
cljs.user=>
cljs.user=> (js/console.log "hi")
nil
```

## Why not?

Back when Clojurescript was first created, a websocket repl would have been far easier to implement than the browser one we have in Clojurescript today. The reason they didn't do that is because in a production environment, when your users are browsing your advanced compiled website, your js artifacts are coming from the server. So, ideally, we want your repl experience to diverge as little as possible from the behavior that will exist in production. That limitation does make things more complex though. And, for some use cases, that complexity is not actually necessary and, in other use cases, a websocket repl would make things way easier. One neat trick: you can open your `index.html` from the filesystem and it'll work just fine - no web server required. Repling from inside of webworkers is going to be a bit easier too, which is my use case here.

## Contributing

Forks, PRs, issues, etc. are all welcome.

## Todo

- [X] Initial spike.
- [ ] Fix client side error on startup: `#object[InternalError InternalError: too much recursion]`
- [ ] Fix ungraceful shutdown where after restart we get: `Address already in use`
- [ ] Add tests.
- [ ] Add more examples.
- [ ] Add more documentation.
- [ ] Build jack-in sequences for calva, cider/emacs, cursive, etc.
- [ ] improve client switching - it's messy right now.
- [ ] Pass through repl options - some handled by cljs.main, some by repl.ws. Only `-w` is handled by repl.ws right now - the rest is implicit.

## License

EPL, like Clojure. I'd prefer MIT these days, but there's a chance we may want to fold this back into Clojurescript proper one day, if it gets polished up and matures.

## History

`repl.ws` takes it's heratige from [`weasel`](https://github.com/nrepl/weasel) and Clojurescript proper. It's a bit of a smashing up of the two.

At one point a few years ago David Nolen (Clojurescript's lead dev) mentioned that a websocket repl might be interesting. He noticed that [TooTallNate/Java-Websocket](https://github.com/TooTallNate/Java-WebSocket) was a fairly minimal java websocket library, if we ever wanted to take a crack at it. I spiked a minimal thing and integrated it into a Clojurescript fork. I got 90% of the stuff working - socket repls and prepls were working, but not the main, regular repl. I made a super simple example project ([repl-ws](https://github.com/johnmn3/repl-ws)) showing how to use the socket repl parts, using the forked Clojurescript. But I never got around to finishing it and David and others interest kinda waned as Figwheel and shadow-cljs were really starting to shine.

Well, years went by, (dang, 7 years!) and AI was suddenly able to do all our work for us. So, I decided to go back and revisit some old ideas. Also, I've been working on web worker stuff recently and what I really need is a flexible websocket repl server. So yes, I hammered the last 10% of `repl.ws` out of Claude 3.5 Sonnet's brain and with our powers combined, within a day, I had a barely working thing. It's super rought right now - use with caution. Honestly, there were only 2 or 3 blind spots I had that the AI helped me fix and I did the rest by hand - I don't think LLMs are that good at code "in the large" quite yet. This stuff kicks ass thoough y'all.
