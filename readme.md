# Want to master Parts? Check bellow

Read parser, it will be faster since it's a mess of a guide

Declaration:

```
let x = 0;
const y = 0;
```

Comments :

```
"I like wolfs";
```

If statement:

```
if (true) print("Hack");
 else print("Bruh");
```

Functions:

```
fun fib(n) {
    if (n <= 0) return 1; else return fib(n - 1) + fib(n - 2) 
}
```

Default argument:

```
fun fight(should = false) {
    if(should) print("We fight boiz");
    else print("We don't fight boiz");
}
```

Const and let:

```
const x = 0;
x = 1; "Throws error";
```

Create objects and store data:

```
let obj = #> x to 0 <#;
obj.x = 1;
print(obj.x);
```

Create range:

```
let range = 1 to 3;
```

Use for loops with range statement:

```
let range = 1 to 3;
for(range) {
  print(it);
}
```

Make class:

```
class firstClass {
   let x = 0;
   fun init() {
     print(x)
   }
}

print(firstClass.x); "Prints 0";
print(firstClass()); "Prints 0";
```

Extend a class:

```
class firstClass {
   let x = 0;
   fun init() {
     print(x)
   }
}

class secondClass: firstClass {
   let x = 1;
   fun init() {
     print(x)
     super.init()
   }
}
print(secondClass()); "Prints '1 0'";
```

Make a must-implement method in class:

```
class firstClass {
   implement next;
}

class secondClass: firstClass {}
"Throws an error cause there is no 'next' impelementation";
```

Creating custom iterator:
```
class ci: Iterable {
   let top = 10;
   let bottom = 0;
   let current = 0;

   fun hasNext() {
     return (bottom < top) && (current < top) 
   }
   
   fun next() {
     return current + 1;
   }
}

for(ci()) print(it);
```