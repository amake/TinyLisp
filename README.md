# TinyLisp for Java
TinyLisp is a very simple Lisp implementation for Java. It is heavily inspired
by Peter Norvig's [lis.py](http://norvig.com/lispy.html).

![demo](https://user-images.githubusercontent.com/2172537/52416817-d941c680-2b2d-11e9-83b2-f02a218f32bb.gif)

The above demo shows the REPL on Android and illustrates input assist,
auto-formatting, symbol completion, history, and sharing.

## Motivation
Imagine you are working with a proprietary software vendor who provides you with
a native binary compiled for Android. You want to test out the binary, seeing
what kinds of outputs you might get from various inputs.

I was in this position and my first thought was to load it up in a REPL of some
sort. But [JSR
223](https://en.wikipedia.org/wiki/Scripting_for_the_Java_Platform)-compatible
scripting engines did not appear to work on Android, so I decided to make my
own.

Thus the goal of this implementation is to drive a REPL to interact with Java
objects.

## Features
- Basic Lisp things: symbols, lists, strings, numbers, `lambda`, `car`, `cdr`,
  `cons`, `list`, `quote`, etc.
- Numbers are parsed as Java `int` or `double`, but are automatically promoted
  to `BigDecimal` if necessary when performing arithmetic
- First-class support for Java arrays: `[1 2 3]` is parsed as `int[]`, `[0.1 0.2
  0.3]` as `double[]`; mixed or other arrays are `Object[]`
- Java `null`, `true`, `false`
- Android compatibility

## Extras
- [cmd](./cmd):
  - A command-line interpreter that can execute files or be a REPL
  - A command-line formatter that auto-formats code in an opinionated way
- [formatter](./formatter): The library that powers the formatter
- [activity](./activity): An Android library providing a TinyLisp REPL activity
- [app](./app): An Android app for the activity

## Usage
### Command line
Binaries are available in
[Releases](https://github.com/amake/TinyLisp/releases). Run the appropriate
executable in the `bin` folder.

TinyLisp interpreter:

```sh
$ echo '(+ 1 2 3)' | ./bin/tinylisp
6
$ echo '(+ 1 2 3)' > program.lisp
$ ./bin/tinylisp program.lisp
6
$ ./bin/tinylisp # No args or stdin launches REPL
```

Formatter:

```sh
$ echo "(let ((a 1)(b 2))   (+ a b)'foo)"| ./bin/tlfmt
(let ((a 1)
      (b 2))
 (+ a b)
 'foo)
```

### Engine
The TinyLisp engine is available as a Maven-style dependency:

```
implementation 'org.tinylisp:engine:+'
```

To execute a TinyLisp program, use the `Engine` and `TLEnvironment` classes as
follows. The result is a `TLExpression`.

```java
Engine engine = new Engine();
TLEnvironment env = Engine.defaultEnvironment();
TLExpression result = engine.execute("(+ 1 2 3)", env); // 6
```

### Formatter
The formatter is also available as a package:

```
implementation 'org.tinylisp:formatter:+'
```

### Android REPL activity
The Android REPL activity is also available as a package:

```
implementation 'org.tinylisp:activity:+'
```

The activity class is `org.tinylisp.activity.ReplActivity`.

## Requirements
The TinyLisp engine targets Java 7 for Android compatibility.

The Android REPL activity targets API 28 (min API 15).

The CLI executables require Java 8+.

## Limitations
- The point is to be small and simple, so many standard commands are missing
  (PRs welcome)
- No thought has been put into speed or efficiency
- [Homoiconicity](https://en.wikipedia.org/wiki/Homoiconicity) goes out the
  window when printing arbitrary Java objects

## Notes
This TinyLisp is unrelated to any other projects with similar names.
