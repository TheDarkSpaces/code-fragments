# code-fragments

This repository contains various code fragments that I amassed.

I've done my best to transparently remove personal data where appropriate.


## Contents


### Minecraft Plugin Layer

* **Language:** Java

Some years ago, my brother got into writing Minecraft plugins for a while.
After a while, I noticed that his starting point was always the same,
including lots of code that he'd copy and paste over, just to modify it a bit.

The basic problem I observed was that every plugin
would have exactly one function to handle all commands,
and this function would then have to determine the actual command
and delegate accordingly.
This meant a huge amount of time would have to be spent
on writing code to delegate,
when that time could have been spent
writing the code to actually execute the requested command.-

I got the idea to abstract away those repetitive basics,
and got started on writing an abstraction layer
to allow for easier and more readable plugin writing
that focuses on the important bits of code.
The idea was to add an Annotation to the method that would handle the command(s)
while a single supertype method would be aware of the annotated methods
and delegate accordingly.

**The code doesn't fully work**
but my brother's interests shifted soon enough,
so I abandoned the project in its current state.


### Windows ISO Preparation

* **Language:** Microsoft PowerShell

When setting up a new Windows 10 system, there are some things that I always do.
A few of those can be taken care of even before the system is installed,
and this script does just that.

**Note that the Remote Server Administration Tools file (`rsat.msu`)
is missing from this repository because of its size!**


### `cue.py`

* **Language:** Python 3

This is a small script meant to read `.cue` Sheet files,
mostly to get the index times.


### `flac.py`

* **Language:** Python 3

A small script that I once used to look at `.flac` file metadata.


### `png_to_text.py`

* **Language:** Python 3

For some reason, my brother once asked me for a way to turn `.png` images
into representations using only few specific colors,
codified in textual form.
This script does just that.

The idea is to read a `.png` image, and convert it into perceptual color space.
Then, each point is rounded to its closest approximation
from the available color selection.
The rounding error is dithered away to the surroundings.


### `split_png.py`

* **Language:** Python 3

I sometimes encounter pictures
in which several discrete elements are present, separated by transparency.
This script can take one such image and split it into its components.


### Phone Root Manual

When I got my phone, the first thing I did was to root it.
While doing so, I wrote this file to keep track of my progress.

I considered converting it to Markdown for easier readability on here,
but in the end I concluded that it's too much effort for too little gain.

The file is meant to be human readable:
* Lines prefixed with `%%` are plain text containing instructions
* Other lines are commands to be executed in a Terminal at the appropriate time.

I may or may not choose to update this file when I get a new phone.


### Math E3

* **Language:** LaTeX

The solutions to my Math course's homework assignments.
This includes both the version I originally submitted,
and a version that implements corrections and suggestions.

The homework assignments can be compiled as standalone exercises,
a single homework assignment at once,
or even all homework assignments into a single file.
Note that `[*]c.tex` files represent corrected versions of `[*].tex`!

**My courses were in German, and so the same applies to this homework!**
