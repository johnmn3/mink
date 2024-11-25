# mink

It's a websocket repl.

## Gist

It can do repls, socket repls and socket prepls using a websocket to the browser. Like so:

### normal repl

```shell
$ clj -M -m mink.main -c ex.core -w src
```

`open index.html`

```clojure
Waiting for connection at ws://localhost:9000 ...
ClojureScript n.n.n
cljs.user=> (+ 1 2)
3
```

### socket repl

```shell
$ clj -J-Dclojure.server.repl="{:port 5555 :accept mink.server/repl}"
```

Then in another terminal:

```shell
$ rlwrap nc localhost 5555
Waiting for connection at ws://localhost:9000 ...
```

`open index.html`

```clojure
Watch compilation log available at: out/watch.log
ClojureScript n.n.n
cljs.user=> (+ 1 2)
3
```

### socket prepl

```shell
$ clj -J-Dclojure.server.prepl="{:port 5555 :accept mink.server/prepl}"
```

Then in another terminal:

```shell
$ rlwrap nc localhost 5556
{:tag :out, :val "Waiting for connection at ws://localhost:9000 ...\n"}
```

`open index.html`

```clojure
{:tag :out, :val "\ncljs.user:=> "}
(+ 1 2)
{:tag :ret, :val "3", :ns "cljs.user", :ms 10, :form "(+ 1 2)"}
```

## Why would you use this?

I personally use `shadow-cljs` most of the time and `figwheel` some of the time when compiling clojurescript to javascript.

But, sometimes, like when I'm building a library, I want to do a quick test to see whether it will work in the browser or it's webworkers. Maybe it's a very simple thing and I don't want to ship any extra dependencies in my repo.

Perhaps I don't want to go through all the ceremony of setting up a new project just for this one off test, nor a figwheel project, or even vite, webpack, whatever. I just want to open a repl and start hacking. I'd rather bring one dependency in, get going, then remove that one extra dep when I'm done - easy.

## How to use

As an example, in an example Clojurescript project called `ex` and with a `deps.edn` like this:

```clojure
{:path ["src"]
 :deps {org.clojure/clojurescript {:mvn/version "n.n.n"}
    	,,,
    	io.github.johnmn3/mink {:git/url "https://github.com/johnmn3/mink"
                                :sha "sha-here"
                                #_#_:exclusions [org.slf4j/slf4j-nop]}}}
 ;; uncomment to this slf4j-nop ^^^^ to enable slf4j logs
```

And a `src/ex/core.cljs` like this:

```clojure
(ns ex.core
  (:require [mink.browser :as otr]))

(when-not (otr/alive?)
  (otr/connect "ws://localhost:9000"))
```

Launch a `mink` repl:

```clojure
$ clj -M -m mink.main -c ex.core -r
Waiting for connection at ws://localhost:9000 ...
```

Then `open index.html` in a browser and when it connects to the repl server it will drop you into a repl:

```clojure
ClojureScript n.n.n
cljs.user=> (+ 1 2)
3
```

## Details

What you know about `cljs.main` should also apply to `mink.main`.

Anything you can do with `cljs.main` you can do with `mink.main`.

`mink.main` passes through all flags that it doesn't need to `cljs.main`.

But if you don't need websocket repl functionality then just use `cljs.main`.

Here's how you would normally start a vanilla repl in a browser (`-re browser` is the default implicit env):

```shell
clj -M -m cljs.main -c hello-world.core -r -w src
```

With `mink` you would do similarly like:

```shell
clj -M -m mink.main -c hello-world.core -r -w src
```

`-w src` means to watch the `src` directory for changes. Files will recompile, the browser will reload and the repl will reconnect when you save a file. (currently only working for socket repl/prepl)

>  Note: passing all other flags to `cljs.main`' is still a work in progress.

## repl switching

Multiple clients can connect to the same repl server.

`:mink/list` will return a list of the currently connected clients and `:mink/=>N` will switch evaluation to the client with the given index.

```clojure
cljs.user=> (require '[mink.server :as otr])
cljs.user=>             	;; open index.html in browser tab
repl client: :mink/=>1
cljs.user=>             	;; open index.html in another browser tab
repl client: :mink/=>2
cljs.user=>             	;; open index.html in yet another browser tab
repl client: :mink/=>3
cljs.user=> :mink/list
:mink/=>1
*:mink/=>2
:mink/=>3
cljs.user=>
cljs.user=> (.log js/console "hi")
nil
cljs.user=> :mink/=>1
:mink/1=>
cljs.user=> (.log js/console "hi")
nil
cljs.user=> :mink/=>3
:mink/3=>
cljs.user=> (.log js/console "hi")
nil
cljs.user=> :mink/list
:mink/=>1
:mink/=>2
*:mink/=>3
```

## socket repl/prepl

First launch a socket repl:

`clj -J-Dclojure.server.repl="{:port 5555 :accept mink.server/repl}"`

Then, in another terminal, connect:

```clojure
 $ rlwrap nc localhost 5555
Waiting for connection at ws://localhost:9000 ...
```

Then, in a browser, load _the actual file_:

`open ./index.html`

The second terminal prompt should return:

```clojure
 $ rlwrap nc localhost 5555
Waiting for connection at ws://localhost:9000 ...
ClojureScript 1.10.XXX
cljs.user=>
```

Print to the browser:

```clojure
cljs.user=> (js/console.log "hi")
nil
```

Check the browser and you should see the "hi" in the console.

Now open another tab with the same file. You'll see information added to the terminal:

```clojure
new repl client: :mink/=>2
cljs.user:1=>
```

Evaluate `:mink/=>2` at the repl to switch the repl to the new client:

```clojure
cljs.user=> :mink/=>2
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

No, this is not a real party repl in the normal sense of the word. For that, you'd want to use something built for just that: [party repl](https://github.com/party-repl/clojure-party-repl). The point here is that this repl can be both multi-client and multi-user. There's some bugs around that right now, but this tool should be generally capable of that. To make a more complete _party repl_ impl, you'd want to be securing your tunnels between the repl and the users.

## Why not?

Back when Clojurescript was first created, a websocket repl would have been far easier to implement than the browser one we have in Clojurescript today. The reason they didn't do that is because in a production environment, when your users are browsing your advanced compiled website, your js artifacts are coming from the server. So, ideally, we want your repl experience to diverge as little as possible from the behavior that will exist in production. That limitation does make things more complex though. And, for some use cases, that complexity is not actually necessary and, in other use cases, a websocket repl would make things way easier. One neat trick: you can open your `index.html` from the filesystem and it'll work just fine - no web server required. Repling from inside of web workers is going to be a bit easier too, which is my use case here.

## Contributing

Forks, PRs, issues, etc. are all welcome.

## Todo

- [X] Initial spike.
- [X] Fix client side error on startup: `#object[InternalError InternalError: too much recursion]`
- [ ] Fix ungraceful shutdown where after restart we get: `Address already in use`
- [ ] Add tests.
- [ ] Add more examples.
- [ ] Add more documentation.
- [X] Auto generate `index.html` if not present. (Using CLJS here)
- [ ] Build jack-in sequences for calva, cider/emacs, cursive, etc.
- [ ] improve client switching - it's messy right now.
- [\] Pass through repl options - some handled by cljs.main, some by mink. Only `-w` is handled by mink right now - the rest is implicit. (semi working)

## License

EPL, like Clojure. I'd prefer MIT these days, but there's a chance we may want to fold this back into Clojurescript proper one day, if it gets polished up and matures. And I pulled a lot of code out of cljs whole cloth, so there's that.

## History

`mink` takes its heritage from [`weasel`](https://github.com/nrepl/weasel) and Clojurescript proper. It's a bit of a smashing up of the two.

At one point a few years ago David Nolen (Clojurescript's lead dev) mentioned that a websocket repl might be interesting. He noticed that [TooTallNate/Java-Websocket](https://github.com/TooTallNate/Java-WebSocket) was a fairly minimal java websocket library, if we ever wanted to take a crack at it. I spiked a minimal thing and integrated it into a Clojurescript fork. I got 90% of the stuff working - socket repls and prepls were working, but not the main, regular repl. I made a super simple example project ([repl-ws](https://github.com/johnmn3/repl-ws)) showing how to use the socket repl parts, using the forked Clojurescript. But I never got around to finishing it and David and others' interest kinda waned as Figwheel and shadow-cljs were really starting to shine.

Well, years went by, (dang, 7 years!) and AI was suddenly able to do all our work for us. So, I decided to go back and revisit some old ideas. Also, I've been working on web worker stuff recently and what I really need is a flexible websocket repl server. So yes, I hammered the last 10% of `mink` out of Claude 3.5 Sonnet's brain and with our powers combined, within a day, I had a barely working thing. It's super rough right now - use with caution. Honestly, there were only 2 or 3 blind spots I had that the AI helped me fix and I did the rest by hand - I don't think LLMs are that good at code "in the large" quite yet. This stuff kicks ass thoough y'all.


