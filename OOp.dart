import 'dart:io';
import 'dart:convert';

// Step 1: Object-Oriented Model with Classes and Inheritance
abstract class Animal {
  void makeSound();
}

class Dog extends Animal {
  @override
  void makeSound() {
    print("Woof!");
  }
}

// Step 2: Class Implementing an Interface
abstract class Vehicle {
  void move();
}

class Car implements Vehicle {
  @override
  void move() {
    print("The car is moving.");
  }
}

// Step 3: Class Overriding an Inherited Method
class Cat extends Animal {
  @override
  void makeSound() {
    print("Meow!");
  }
}

// Step 4: Instance Initialized with Data from a File
class Person {
  String name;
  int age;

  Person(this.name, this.age);

  static Person fromFile(String filePath) {
    final file = File(filePath);
    final content = file.readAsStringSync();
    final data = jsonDecode(content);
    return Person(data['name'], data['age']);
  }
}

// Step 5: Method Demonstrating the Use of a Loop
void printNames(List<String> names) {
  for (var name in names) {
    print(name);
  }
}

void main() {
  // Example usage
  Dog dog = Dog();
  dog.makeSound();

  Car car = Car();
  car.move();

  Cat cat = Cat();
  cat.makeSound();

  // Reading data from a file and initializing a Person instance
  Person person = Person.fromFile('data.json');
  print('Name: ${person.name}, Age: ${person.age}');

  // Demonstrating the use of a loop
  printNames(['Alice', 'Bob', 'Charlie']);
}