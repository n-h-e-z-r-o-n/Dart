void main() {

  int sum(int a, int b) {
    return a + b;
  }

  for (int i = 1; i <= 10; i++) {
    print(i);
  }


  String input = 'apple';
  switch (input) {
    case 'apple':
      print('You selected apple.');
      break;
    case 'banana':
      print('You selected banana.');
      break;
    default:
      print('Unknown selection.');
  }
}