# JComboBoxExperiment Test Suite

This directory contains the test suite for the JComboBoxExperiment class and related JComponents.

## Test Suite Overview

The `JComboBoxExperimentTest.java` file contains comprehensive tests for JComboBox functionality, focusing on the core features that would be used by the JComboBoxExperiment class. The test suite uses basic Java assertions and standard Swing components.

## Running the Tests

### From Command Line
```bash
cd src/test/java/plugins/fmp/multiSPOTS96/tools/JComponents
javac JComboBoxExperimentTest.java
java JComboBoxExperimentTest
```

### From IDE
1. Open the `JComboBoxExperimentTest.java` file
2. Run the `main` method
3. View the test results in the console

## Test Categories

The test suite includes the following test categories:

### 1. Constructor Tests
- JComboBox instance creation
- Default value initialization

### 2. Basic Functionality Tests
- Item removal (removeAllItems)
- Empty combo box operations
- Basic item addition and retrieval

### 3. Item Management Tests
- Multiple item addition
- Duplicate item handling
- Specific item removal
- Item selection

### 4. Search Tests
- Finding item index by value
- Finding item by value
- Null value handling in search

### 5. List Management Tests
- Getting items as list
- Setting items from list
- Empty list handling
- Null list handling
- Order preservation

### 6. Performance Tests
- Large number of items handling
- Operation timing validation
- Memory efficiency testing

## Test Results

The test suite provides detailed output including:
- Individual test results (PASS/FAIL)
- Error messages for failed tests
- Summary statistics
- Success rate percentage

## Example Output

```
=== JComboBoxExperiment Test Suite ===
Starting tests...

--- Constructor Tests ---
Test 1: Should create JComboBox instance ... PASS
Test 2: Should initialize with correct default values ... PASS

--- Basic Functionality Tests ---
Test 3: Should remove all items ... PASS
Test 4: Should handle empty combo box operations ... PASS
Test 5: Should add and retrieve items ... PASS

...

=== Test Summary ===
Total tests: 15
Passed: 15
Failed: 0
Success rate: 100%
All tests passed!
```

## Test Environment

The test suite:
- Creates temporary directories for testing
- Automatically cleans up test files
- Uses isolated test environments
- Provides detailed error reporting
- Uses standard Swing JComboBox for testing

## Adding New Tests

To add new tests:

1. Create a new test method in the appropriate test group
2. Use the `test()` method with a descriptive name and test logic
3. Use the assertion methods: `assertTrue()`, `assertFalse()`, `assertEquals()`, `assertNotNull()`, `assertNull()`
4. Handle exceptions appropriately with try-catch blocks

Example:
```java
test("Should handle new functionality", () -> {
    try {
        // Test logic here
        assertTrue(condition, "Description of what should be true");
    } catch (Exception e) {
        fail("Test failed: " + e.getMessage());
    }
});
```

## Utility Methods

The test suite includes several utility methods that mimic the functionality of JComboBoxExperiment:

- `findItemIndex()` - Finds the index of an item in the combo box
- `findItem()` - Finds an item by value
- `getItemsAsList()` - Converts combo box items to a list
- `setItemsFromList()` - Sets combo box items from a list

## Troubleshooting

### Common Issues

1. **Swing Thread Issues**: The tests use Swing components but don't run on the EDT. This is acceptable for unit testing.
2. **Memory Issues**: The test suite may use significant memory for large-scale tests
3. **Timeout Issues**: Some tests have timeouts; increase if needed for slower systems

### Debug Mode

To run tests with more detailed output, modify the `test()` method to include additional logging.

## Dependencies

The test suite requires only standard Java libraries:
- `java.io.File`
- `java.nio.file.Files`
- `java.util.List`
- `java.util.ArrayList`
- `javax.swing.JComboBox`

No external testing frameworks are required.

## Relationship to JComboBoxExperiment

This test suite focuses on the core functionality that JComboBoxExperiment would use:

- **Item Management**: Tests adding, removing, and managing items in a combo box
- **Search Functionality**: Tests finding items by name/value (similar to `getExperimentIndexFromExptName()`)
- **List Operations**: Tests converting between combo box and list representations
- **Performance**: Tests handling large numbers of items efficiently

The tests use String items instead of Experiment objects to avoid dependencies on the Experiment class, but the core functionality tested is the same as what JComboBoxExperiment would use.

## Future Enhancements

When the full plugin environment is available, this test suite can be extended to:

1. Test actual JComboBoxExperiment methods
2. Test with real Experiment objects
3. Test experiment chaining functionality
4. Test field value extraction methods
5. Test Excel export integration 